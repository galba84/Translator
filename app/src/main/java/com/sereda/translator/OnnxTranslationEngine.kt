package com.sereda.translator

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.text.BreakIterator
import java.util.Locale

/** Single-owner sessions; the pipeline runs on one dedicated inference thread. */
class OnnxTranslationEngine(private val resources: BundledResources) : TranslationEngine {
    private val environment = OrtEnvironment.getEnvironment()

    override fun translate(text: String, source: Language, target: Language, cancelled: () -> Boolean): String {
        var current = text
        for (pair in TranslationRoutes.pairs(source, target)) {
            checkActive(cancelled)
            current = translatePair(current, pair, cancelled)
        }
        return current
    }

    private fun translatePair(text: String, pair: String, cancelled: () -> Boolean): String {
        val directory = resources.file("translation/$pair")
        val tokenizer = MarianTokenizer(directory)
        require(text.length <= 4000) { "Translate a few sentences at a time (up to 4,000 characters)." }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        options.use {
            environment.createSession(directory.resolve("encoder_model_quantized.onnx").path, options).use { encoder ->
                environment.createSession(directory.resolve("decoder_model_quantized.onnx").path, options).use { first ->
                    environment.createSession(directory.resolve("decoder_with_past_model_quantized.onnx").path, options).use { cached ->
                        return text.split('\n').joinToString("\n") { paragraph ->
                            val iterator = BreakIterator.getSentenceInstance(Locale.forLanguageTag(pair.take(2)))
                            iterator.setText(paragraph)
                            var from = iterator.first()
                            val output = ArrayList<String>()
                            var end = iterator.next()
                            while (end != BreakIterator.DONE) {
                                checkActive(cancelled)
                                val sentence = paragraph.substring(from, end).trim()
                                if (sentence.isNotEmpty()) {
                                    val ids = tokenizer.encode(sentence)
                                    tensor(ids).use { input ->
                                        tensor(LongArray(ids.size) { 1 }).use { mask ->
                                            encoder.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { encoded ->
                                                output.add(decode(tokenizer, encoded[0] as OnnxTensor, mask, first, cached, cancelled))
                                            }
                                        }
                                    }
                                }
                                from = end
                                end = iterator.next()
                            }
                            output.joinToString(" ")
                        }
                    }
                }
            }
        }
    }

    private fun decode(
        tokenizer: MarianTokenizer, hidden: OnnxTensor, mask: OnnxTensor,
        first: OrtSession, cached: OrtSession, cancelled: () -> Boolean
    ): String {
                var result: OrtSession.Result? = null
                // Cross-attention caches from the first step remain owned by the first result.
                var firstResult: OrtSession.Result? = null
                var token = tokenizer.start
                val generated = ArrayList<Int>()
                try {
                    val limit = 384
                    for (step in 0 until limit) {
                        checkActive(cancelled)
                        val decoder = if (step == 0) first else cached
                        tensor(longArrayOf(token.toLong())).use { input ->
                            val inputs = HashMap<String, OnnxTensor>()
                            inputs["input_ids"] = input
                            if ("encoder_attention_mask" in decoder.inputNames) inputs["encoder_attention_mask"] = mask
                            if ("encoder_hidden_states" in decoder.inputNames) inputs["encoder_hidden_states"] = hidden
                            if (step > 0) {
                                for (name in decoder.inputNames.filter { it.startsWith("past_key_values.") }) {
                                    val output = name.replace("past_key_values.", "present.")
                                    val previous = if (name.contains(".encoder.")) firstResult!! else result!!
                                    inputs[name] = previous.get(output).orElseThrow() as OnnxTensor
                                }
                            }
                            val next = decoder.run(inputs)
                            if (result !== firstResult) result?.close()
                            result = next
                            if (step == 0) firstResult = next
                            val logits = (next.get("logits").orElseThrow() as OnnxTensor).floatBuffer
                            var maximum = Float.NEGATIVE_INFINITY
                            var best = tokenizer.eos
                            // Input is exactly one decoder token; logits shape is [1,1,vocabulary].
                            for (index in 0 until logits.remaining()) {
                                val score = logits.get(index)
                                if (index != tokenizer.pad && score > maximum) { maximum = score; best = index }
                            }
                            token = best
                        }
                        if (token == tokenizer.eos) return tokenizer.decode(generated)
                        generated.add(token)
                    }
                    error("Translation exceeded the output limit. Try a shorter passage.")
                } finally {
                    if (result !== firstResult) result?.close()
                    firstResult?.close()
                }
    }

    private fun tensor(values: LongArray): OnnxTensor = OnnxTensor.createTensor(
        environment, LongBuffer.wrap(values), longArrayOf(1, values.size.toLong())
    )
}

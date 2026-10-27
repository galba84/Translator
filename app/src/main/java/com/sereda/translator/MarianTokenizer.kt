package com.sereda.translator

import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Unigram Viterbi segmentation over the model's exported SentencePiece vocabulary. */
class MarianTokenizer(directory: File) {
    private class Node {
        val children = HashMap<Char, Node>()
        var id = -1
        var score = Double.NEGATIVE_INFINITY
    }
    private val trie = Node()
    private val vocabulary = JSONObject(File(directory, "vocab.json").readText())
    private val config = JSONObject(File(directory, "config.json").readText())
    private val pieces = HashMap<Int, String>()
    val eos = config.getInt("eos_token_id")
    val pad = config.getInt("pad_token_id")
    val start = config.getInt("decoder_start_token_id")
    private val unknown = vocabulary.optInt("<unk>", 1)
    private var minimumScore = 0.0

    init {
        for (key in vocabulary.keys()) pieces[vocabulary.getInt(key)] = key
        // Marian's joint vocabulary contains target-only pieces. Segment using ONLY source.spm.
        for ((piece, score) in sentencePieces(File(directory, "source.spm").readBytes())) {
            if (!vocabulary.has(piece) || piece.isEmpty()) continue
            var node = trie
            for (char in piece) node = node.children.getOrPut(char) { Node() }
            node.id = vocabulary.getInt(piece)
            node.score = score
            minimumScore = minOf(minimumScore, node.score)
        }
    }

    private fun sentencePieces(bytes: ByteArray): List<Pair<String, Double>> {
        val result = ArrayList<Pair<String, Double>>()
        val model = Proto(bytes)
        while (model.remaining()) {
            val tag = model.number()
            if (tag == 10) {
                val piece = Proto(model.blob())
                var text = ""
                var score = 0.0
                var type = 1
                while (piece.remaining()) {
                    when (val field = piece.number()) {
                        10 -> text = piece.blob().toString(Charsets.UTF_8)
                        21 -> score = piece.float().toDouble()
                        24 -> type = piece.number()
                        else -> piece.skip(field)
                    }
                }
                if (type == 1 || type == 4) result.add(text to score)
            } else model.skip(tag)
        }
        return result
    }

    private class Proto(val bytes: ByteArray) {
        var offset = 0
        fun remaining() = offset < bytes.size
        fun number(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val byte = bytes[offset++].toInt() and 255
                result = result or ((byte and 127) shl shift)
                if (byte < 128) return result
                shift += 7
                require(shift < 35) { "Invalid SentencePiece data" }
            }
        }
        fun blob(): ByteArray {
            val length = number()
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }
        fun float(): Float = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float.also { offset += 4 }
        fun skip(tag: Int) { when (tag and 7) {
            0 -> number()
            1 -> offset += 8
            2 -> { val n = number(); offset += n }
            5 -> offset += 4
            else -> error("Invalid SentencePiece field")
        } }
    }

    fun encode(text: String): LongArray {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
        val result = ArrayList<Long>()
        for (word in normalized.trim().split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            result.addAll(segment("▁$word").map(Int::toLong))
        }
        result.add(eos.toLong())
        require(result.size <= 480) { "This passage is too long. Translate a few sentences at a time." }
        return result.toLongArray()
    }

    private fun segment(text: String): List<Int> {
        val scores = DoubleArray(text.length + 1) { Double.NEGATIVE_INFINITY }
        val previous = IntArray(text.length + 1)
        val ids = IntArray(text.length + 1) { unknown }
        scores[0] = 0.0
        for (from in text.indices) {
            if (!scores[from].isFinite()) continue
            var node = trie
            var end = from
            var hasSingle = false
            val pointEnd = from + Character.charCount(text.codePointAt(from))
            while (end < text.length) {
                node = node.children[text[end]] ?: break
                end++
                if (node.id < 0) continue
                if (end == pointEnd) hasSingle = true
                val candidate = scores[from] + node.score
                if (candidate > scores[end]) {
                    scores[end] = candidate; previous[end] = from; ids[end] = node.id
                }
            }
            if (!hasSingle && scores[from] + minimumScore - 10 > scores[pointEnd]) {
                scores[pointEnd] = scores[from] + minimumScore - 10
                previous[pointEnd] = from; ids[pointEnd] = unknown
            }
        }
        val reversed = ArrayList<Int>()
        var position = text.length
        while (position > 0) {
            val id = ids[position]
            if (id != unknown || reversed.lastOrNull() != unknown) reversed.add(id)
            val next = previous[position]
            check(next < position) { "Tokenizer could not segment input" }
            position = next
        }
        return reversed.asReversed()
    }

    fun decode(ids: List<Int>): String = ids.filter { it != eos && it != pad }
        .joinToString("") { pieces[it].orEmpty() }.replace("▁", " ").trim()
}

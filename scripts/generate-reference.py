"""Development-only reference fixtures; requires sentencepiece and onnxruntime==1.23.2.

Run from the repository root. Never used by the installed application.
"""
import json
from pathlib import Path
import time
import re
import numpy as np
import onnxruntime as ort
import sentencepiece as spm

ROOT = Path('app/src/main/assets/models/translation')
SAMPLES = {
    'en': ['Hello, how are you?', 'I need some water.', 'Where is the train station?',
           "It's a beautiful day. I would like a cup of coffee.", 'The price is 12.50 euros.',
           'Good morning! Can you help me? Thank you very much.'],
    'es': ['Buenos días. ¿Cómo estás?', 'Necesito agua, por favor.', '¿Dónde está la estación de tren?',
           '¡Muchas gracias! Me gustaría una taza de café.', 'El precio es de 12,50 euros.',
           'Buenos días. ¿Puede ayudarme? Muchas gracias.']
}

def tokenizer_fixtures():
    result = []
    for pair in ['en-es', 'es-en']:
        folder = ROOT / pair
        tokenizer = spm.SentencePieceProcessor(model_file=str(folder / 'source.spm'))
        vocab = json.loads((folder / 'vocab.json').read_text())
        for text in SAMPLES[pair[:2]]:
            ids = [vocab.get(piece, vocab['<unk>']) for piece in tokenizer.encode(text, out_type=str)] + [vocab['</s>']]
            result.append(dict(pair=pair, text=text, ids=ids))
    target = Path('app/src/test/resources/tokenizer-reference.json')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')

def translate_pair(text, pair):
    folder = ROOT / pair
    config = json.loads((folder / 'config.json').read_text())
    vocab = json.loads((folder / 'vocab.json').read_text())
    reverse = {v:k for k,v in vocab.items()}
    tokenizer = spm.SentencePieceProcessor(model_file=str(folder / 'source.spm'))
    ids = [vocab.get(piece, vocab['<unk>']) for piece in tokenizer.encode(text, out_type=str)] + [vocab['</s>']]
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    encoder = ort.InferenceSession(str(folder / 'encoder_model_quantized.onnx'), options)
    mask = np.ones((1, len(ids)), dtype=np.int64)
    hidden = encoder.run(None, {'input_ids':np.array([ids],dtype=np.int64), 'attention_mask':mask})[0]
    del encoder
    first = ort.InferenceSession(str(folder / 'decoder_model_quantized.onnx'), options)
    cached = ort.InferenceSession(str(folder / 'decoder_with_past_model_quantized.onnx'), options)
    token = config['decoder_start_token_id']
    generated, cache = [], {}
    for step in range(384):
        decoder = first if step == 0 else cached
        inputs = {'input_ids':np.array([[token]],dtype=np.int64), 'encoder_attention_mask':mask}
        if step == 0: inputs['encoder_hidden_states'] = hidden
        else: inputs.update(cache)
        outputs = dict(zip([o.name for o in decoder.get_outputs()], decoder.run(None,inputs)))
        for key, value in outputs.items():
            if key.startswith('present.'): cache[key.replace('present.', 'past_key_values.')] = value
        logits = outputs['logits'][0,-1].copy()
        logits[config['pad_token_id']] = -np.inf
        token = int(logits.argmax())
        if token == config['eos_token_id']: break
        generated.append(token)
    else: raise RuntimeError('Output limit exceeded')
    return ''.join(reverse[i] for i in generated).replace('▁',' ').strip()

if __name__ == '__main__':
    tokenizer_fixtures()
    translations = []
    for source, target in [('en','es'),('es','en')]:
        text = SAMPLES[source][-1]
        start = time.perf_counter()
        output = text
        pairs = [source+'-'+target]
        for pair in pairs:
            output = ' '.join(translate_pair(sentence,pair) for sentence in re.split(r'(?<=[.!?])\s+', output) if sentence.strip())
        record = dict(source=source,target=target,text=text,result=output,sentences=3,words=len(text.split()),desktop_seconds=round(time.perf_counter()-start,3))
        print(json.dumps(record,ensure_ascii=False),flush=True)
        translations.append(record)
    target = Path('app/src/androidTest/assets/translation-reference.json')
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(translations,ensure_ascii=False,indent=2),encoding='utf-8')

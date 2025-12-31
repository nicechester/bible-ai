# Models Directory

Place your GGUF model files here.

## Default Model

By default, the application looks for `model.gguf` in this directory.

You can override the path using the `LLAMA_MODEL_PATH` environment variable or by updating `application.yml`.

## Example

Download a model and place it here:

```bash
# Example: Download Mistral 7B
wget https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF/resolve/main/mistral-7b-instruct-v0.2.Q3_K_M.gguf -O model.gguf
```

Or rename your model file to `model.gguf`.

## Supported Formats

- GGUF format models compatible with llama.cpp
- Recommended: Quantized models (Q3_K_M, Q4_K_M, Q5_K_M) for better performance


# Embedding / RAG

Modelos de embedding convertem texto em vetores numéricos que capturam significado. O 橘子岛 usa esses vetores para busca semântica (RAG) no seu histórico de conversas — encontrando mensagens pelo que elas significam, não apenas pelas palavras que contêm.

## Como Funciona

1. Cada mensagem é enviada a um modelo de embedding
2. O modelo retorna um vetor (uma lista de números) representando o significado da mensagem
3. Quando você busca, sua consulta também é convertida em embedding
4. O 橘子岛 calcula a **similaridade de cosseno** entre o vetor da consulta e todos os vetores de mensagens
5. As mensagens com similaridade acima do seu limiar são retornadas como correspondências

## Provedores Suportados

| Provedor | URL Base | Requer Chave de API | Observações |
|----------|----------|---------------------|-------------|
| **OpenAI** | `https://api.openai.com/v1` | Sim | `text-embedding-3-small`, `text-embedding-3-large` |
| **Mistral** | `https://api.mistral.ai/v1` | Sim | `mistral-embed` |
| **Voyage AI** | `https://api.voyageai.com/v1` | Sim | `voyage-3`, `voyage-3-lite` |
| **SiliconFlow** | `https://api.siliconflow.cn/v1` | Sim | `BAAI/bge-large-zh-v1.5` (otimizado para chinês) |
| **Ollama** | `http://localhost:11434/v1` | Não | `qwen3-embedding`, `nomic-embed-text`, etc. |
| **Personalizado** | Qualquer | Opcional | Qualquer endpoint de embeddings compatível com OpenAI |
| **Local** | N/A | Não | Modelos GGUF de embedding via llama.cpp |

---

## Adicionando um Modelo de Embedding

### Remoto (API)

1. Vá em **Configurações → Busca em Conversas**
2. Toque em **Adicionar Modelo Remoto**
3. Configure:

| Campo | Descrição |
|-------|-----------|
| **Provedor** | Selecione no menu suspenso (OpenAI, Mistral, Voyage, SiliconFlow, Ollama, Personalizado) |
| **Nome do Modelo** | O ID exato do modelo (ex.: `text-embedding-3-small`) |
| **URL Base** | Preenchido automaticamente para provedores conhecidos; editável para proxies |
| **Chave de API** | Deixe em branco para resolver automaticamente da sua chave de provedor de chat, ou insira uma chave dedicada |
| **Tamanho do Lote** | Mensagens a enviar por requisição de API (1–100) |

4. Toque em **Adicionar** — um teste de conexão é executado antes de salvar

!!! tip
    O campo de chave de API é opcional se você já configurou o mesmo provedor para chat. Deixe em branco e o 橘子岛 resolve sua chave de API de chat automaticamente.

### Local (GGUF)

1. Vá em **Configurações → Busca em Conversas**
2. Toque em **Adicionar Modelo Local**
3. Importe um arquivo de modelo de embedding `.gguf` (ex.: `bge-small-en-v1.5-q4_k.gguf`)
4. Dê um nome a ele
5. Toque em **Adicionar**

Modelos de embedding são tipicamente muito menores que modelos de chat — no máximo algumas centenas de MB.

### Ollama

1. Instale o Ollama em uma máquina
2. Baixe um modelo de embedding: `ollama pull qwen3-embedding:8b`
3. No 橘子岛, adicione um modelo remoto:
    - Provedor: **Ollama**
    - URL Base: `http://<host>:11434/v1`
    - Nome do modelo: `qwen3-embedding:8b` (inclua a `:tag`)
    - Chave de API: deixe em branco
4. Toque em **Adicionar**

!!! note
    Tags de sufixo do Ollama como `:8b`, `:latest` fazem parte do nome do modelo. Use o nome exato do `ollama list`.

---

## Cache

Após adicionar um modelo, você precisa armazenar suas mensagens em cache (gerar embeddings):

1. Toque em **Cache** no modelo de embedding
2. O 橘子岛 processa todas as mensagens não armazenadas em lotes
3. Um indicador de progresso circular mostra o progresso atual
4. Conclusão: "Todas as N mensagens em cache"

### Cache Automático

Ative **Cache automático** para gerar embeddings automaticamente para novas mensagens conforme elas chegam. Isso mantém seu índice de busca sempre atualizado.

### Re-Cache

Toque em **Re-cache** para excluir todos os embeddings existentes e reconstruir do zero. Use quando:

- Mudar para um modelo de embedding diferente
- A qualidade do embedding parecer degradada
- O cache estiver inconsistente

!!! warning
    O re-cache não pode ser desfeito e pode levar muito tempo para históricos de mensagens grandes.

---

## Tamanho do Lote

A configuração de **Tamanho do Lote** (1–100) controla quantas mensagens são enviadas por requisição de API durante o cache:

- **Maior**: Cache mais rápido, mas payloads de API maiores
- **Menor**: Requisições menores, mais lento mas mais confiável em conexões lentas

Comece com o padrão e ajuste se encontrar timeouts (diminua) ou quiser cache mais rápido (aumente).

---

## Testando Sua Configuração

Quando você adiciona um modelo remoto, o 橘子岛 executa um teste de conexão automático. Se falhar:

1. Verifique o nome do modelo — inclua tags para Ollama (`:8b`, `:latest`)
2. Verifique se a URL base é alcançável do seu dispositivo
3. Confirme que a chave de API é válida (se necessária)
4. Tente um nome de modelo conhecido para esse provedor

Erros comuns:
- **"Nome do modelo errado"** — verifique a grafia exata, incluindo tags
- **"URL base errada"** — certifique-se de que o endpoint suporta `/v1/embeddings`
- **"Chave de API ausente"** — alguns provedores exigem autenticação
- **"Erro de rede"** — verifique a conectividade

---

## Recomendações de Provedores

| Caso de Uso | Provedor Recomendado |
|-------------|----------------------|
| **Melhor qualidade (Inglês)** | Voyage AI `voyage-3` |
| **Melhor qualidade (Chinês)** | SiliconFlow `BAAI/bge-large-zh-v1.5` |
| **Gratuito / Auto-hospedado** | Ollama `qwen3-embedding` ou `nomic-embed-text` |
| **Totalmente offline** | GGUF Local `bge-small-en-v1.5` |
| **Já usa OpenAI** | OpenAI `text-embedding-3-small` (barato, rápido) |

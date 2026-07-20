# Modelos Locais

Execute LLMs diretamente no seu dispositivo Android usando arquivos de modelo GGUF e llama.cpp. Sem necessidade de rede, sem chaves de API, totalmente privado.

## Como Funciona

O 橘子岛 inclui o llama.cpp via Android NDK (CMake). Quando você importa um arquivo GGUF, o modelo é executado inteiramente na CPU do seu dispositivo — nenhum dado sai do dispositivo.

## Requisitos

- Apenas modelos no **formato GGUF** (o padrão para llama.cpp)
- **Memória do dispositivo**: O modelo deve caber na RAM disponível. Como regra geral:
    - Modelos de 1–3B parâmetros: 4–6 GB de RAM
    - Modelos de 7–8B parâmetros: 6–8 GB de RAM
- **Armazenamento**: Arquivos GGUF variam de ~500 MB (modelos pequenos quantizados) a 5+ GB

!!! warning
    A inferência local é intensiva em CPU e muito mais lenta que APIs na nuvem. É melhor para uso offline, conteúdo sensível à privacidade ou experimentação — não para chat rápido e de alto volume.

---

## Importando um Modelo de Chat

1. Baixe um arquivo de modelo GGUF para o seu dispositivo (consulte as fontes recomendadas abaixo)
2. Vá em **Configurações → Provedor**
3. Selecione **Local** como provedor
4. Toque em **Importar Modelo GGUF**
5. Selecione o arquivo `.gguf` do seu dispositivo
6. Configure o modelo:

| Parâmetro | Descrição | Exemplo |
|-----------|-----------|---------|
| **ID do Modelo** | Identificador em minúsculas, sem espaços | `qwen3-8b` |
| **Alias** | Nome de exibição | `Qwen 3 8B` |
| **Tamanho do Contexto** | Janela de contexto máxima em tokens | `4096` |
| **Temperatura** | Aleatoriedade (0,0–2,0) | `0,7` |
| **Top P** | Limiar de amostragem por núcleo (0,0–1,0) | `0,9` |
| **Máx. de Tokens** | Comprimento máximo de geração | `2048` |

7. Toque em **Adicionar**

O modelo é importado e está pronto para uso imediatamente.

---

## Importando um Modelo de Embedding

Modelos de embedding são menores e usados para busca semântica:

1. Vá em **Configurações → Busca em Conversas**
2. Toque em **Adicionar Modelo Local**
3. Selecione um arquivo de modelo de embedding `.gguf`
4. Dê um nome a ele
5. Toque em **Adicionar**

Consulte [Embedding / RAG](embedding.md) para configuração da busca.

---

## Selecionando o Modelo Ativo

Após importar um ou mais modelos:

1. Vá em **Configurações → Provedor → Local**
2. Você verá todos os modelos importados listados
3. Toque no **botão de rádio** ao lado do modelo que deseja usar
4. O modelo selecionado se torna ativo quando **Local** é escolhido como provedor de chat

---

## Gerenciando Modelos Locais

### Renomear

Toque em um modelo para alterar seu alias ou ajustar parâmetros (temperatura, tamanho do contexto, etc.).

### Excluir

Pressione longamente um modelo e toque em **Excluir**. Isso remove o modelo do 橘子岛 e exclui o arquivo GGUF do armazenamento.

---

## Modelos Recomendados

### Modelos de Chat

| Modelo | Tamanho | RAM Necessária | Observações |
|--------|---------|----------------|-------------|
| Qwen 3 1.7B | ~1 GB | 3–4 GB | Boa qualidade para seu tamanho |
| Llama 3.2 3B | ~2 GB | 4–5 GB | Sólido para uso geral |
| Qwen 3 8B | ~5 GB | 7–8 GB | Melhor qualidade, RAM alta |

### Modelos de Embedding

| Modelo | Tamanho | Observações |
|--------|---------|-------------|
| BGE Small EN v1.5 | ~130 MB | Bons embeddings em inglês |
| BGE Small ZH v1.5 | ~130 MB | Otimizado para chinês |
| Nomic Embed Text v1.5 | ~270 MB | Bom multilingue |

### Onde Obter Arquivos GGUF

- [Hugging Face](https://huggingface.co/models?library=gguf) — pesquise por "GGUF"
- [Modelos quantizados do bartowski](https://huggingface.co/bartowski) — ampla seleção, bem organizado

!!! tip
    Procure pela quantização `Q4_K_M` — ela oferece o melhor equilíbrio entre qualidade e tamanho para modelos de chat.

---

## Dicas de Desempenho

- **Contexto menor = mais rápido**: Comece com 2048 e aumente apenas se necessário
- **Quantização menor = mais rápido**: Q4_K_M é mais rápido que Q6_K ou Q8
- **Feche outros aplicativos**: A inferência local precisa do máximo de RAM possível
- **Conecte na tomada**: A inferência é intensiva em CPU, e o uso prolongado drena a bateria

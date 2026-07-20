# Busca em Conversas

O 橘子岛 pode buscar em todo o seu histórico de conversas — seja por correspondência de palavras-chave ou por recuperação semântica (baseada em significado) usando modelos de embedding.

## Métodos de Busca

### Busca por Palavra-chave

Correspondência de texto rápida e exata. A busca procura ocorrências literais da sua consulta no conteúdo das mensagens.

**Ideal para:**
- Encontrar uma frase ou termo específico
- Consultas rápidas quando você lembra as palavras exatas
- Configuração zero — funciona imediatamente

**Limitações:**
- Não encontra sinônimos e conceitos relacionados
- Sem compreensão de significado

### Busca Semântica (RAG)

Usa modelos de embedding para encontrar mensagens por **significado**, não por palavras exatas. Uma consulta por "como configurar o banco de dados" pode encontrar mensagens sobre "configuração do Room" mesmo que a palavra "banco de dados" nunca apareça.

**Ideal para:**
- Encontrar conversas por tópico ou tema
- Consultas amplas onde você não lembra a frase exata
- Descobrir discussões relacionadas em diferentes conversas

**Requisitos:**
- Um modelo de embedding deve estar configurado (consulte [Embedding / RAG](embedding.md))
- As mensagens devem estar em cache (embeddings gerados)

---

## Configuração

### 1. Adicionar um Modelo de Embedding

Consulte [Embedding / RAG](embedding.md) para configuração detalhada. Você pode usar:
- **Modelos remotos** (OpenAI, Mistral, Voyage, Ollama, etc.)
- **Modelos locais** (arquivos GGUF, totalmente offline)

### 2. Escolher Métodos de Busca

Em **Configurações → Busca em Conversas**:

| Configuração | Descrição |
|--------------|-----------|
| **Método de Busca do Modelo** | Como o modelo busca quando chama a ferramenta `search_conversations` |
| **Método de Busca Manual** | Como a barra de busca na gaveta de conversas funciona |

Defina cada um como **Palavra-chave** ou **Semântica (RAG)**.

### 3. Configurar Escopo da Busca

| Configuração | Faixa | Descrição |
|--------------|-------|-----------|
| **Mensagens de contexto por resultado** | 4–32 | Quantas mensagens ao redor incluir com cada correspondência (passos: 4, 8, 12, 16, 20, 24, 28, 32) |
| **Máximo de resultados da busca** | 5–30 | Número máximo de correspondências a retornar (passos: 5, 10, 15, 20, 25, 30) |
| **Limiar de Similaridade** | 0,0–1,0 | Apenas RAG: pontuação mínima de similaridade para uma correspondência. Mais alto = mais rigoroso. Padrão: 0,5 |

### 4. Armazenar Mensagens em Cache

Se estiver usando RAG, toque em **Cache** para gerar embeddings para todas as mensagens existentes. Ative **Cache automático** para manter o índice atualizado automaticamente.

---

## Usando a Busca

### Busca Manual (Barra de Busca)

1. Abra a **gaveta de conversas** (menu hambúrguer :material-menu: ou deslize para a direita)
2. Toque na barra de busca no topo
3. Digite sua consulta
4. Os resultados aparecem abaixo — toque em qualquer resultado para abrir essa conversa na mensagem correspondente

### Busca Iniciada pelo Modelo

Quando **Acessar Conversas Passadas** está ativado (Configurações → Memória), o modelo pode buscar seu histórico de forma autônoma:

```text
Você: "O que decidimos sobre o design da API na semana passada?"
Modelo: [Busca "decisão de design da API"]
        "Na última terça-feira, decidimos usar..."
```

A busca aparece como um card de ferramenta na conversa.

---

## Limiar de Similaridade

O controle deslizante de **Limiar de Similaridade** (0,0 a 1,0) controla quão próxima uma mensagem deve corresponder para ser incluída nos resultados RAG:

- **Baixo (0,3–0,5)**: Mais resultados, pode incluir conteúdo vagamente relacionado
- **Médio (0,5–0,7)**: Equilibrado — bom padrão
- **Alto (0,7–0,9)**: Menos resultados, apenas correspondências muito próximas

Comece com o padrão e ajuste com base nos seus resultados. Se você obtiver muitas correspondências irrelevantes, aumente o limiar. Se perder conversas relevantes, diminua-o.

---

## Exibição dos Resultados da Busca

Na gaveta de conversas, os resultados da busca mostram:

- **Título da conversa** (ou "Sem título")
- **Mensagem correspondente** — a mensagem do usuário ou modelo que correspondeu
- **Rótulo do papel** — Usuário ou Modelo
- **Mensagens de contexto** — mensagens ao redor para contexto

Toque em um resultado para abrir a conversa rolada até a mensagem correspondente.

# Memória & Cache

O 橘子岛 possui um sistema de memória persistente que permite ao modelo lembrar informações entre conversas. Combinado com cache automático baseado em embedding, ele fornece uma base de conhecimento que cresce com seu uso.

## Tipos de Memória

### Memória Ativa

Um único contexto de memória sempre ativo que é incluído em **toda chamada de API** ao modelo. Pense nela como um bilhete adesivo que o modelo sempre vê.

**Use a Memória Ativa para:**
- Seu nome, preferências e histórico
- Contexto do projeto que o modelo deve sempre conhecer
- Instruções permanentes que se aplicam a todas as conversas
- Fatos que você está cansado de repetir

**Exemplo de conteúdo da Memória Ativa:**
```text
Usuário: Newo Ether
Preferências: Prefere chinês para conversas casuais, inglês para tópicos técnicos.
Projeto: Construindo o 橘子岛 — um cliente LLM Android BYOK.
Estilo de código: Kotlin, Jetpack Compose, arquitetura MVVM.
```

#### Editando a Memória Ativa

1. Vá em **Configurações → Memória**
2. Role até **Memória Ativa**
3. Toque em **Editar Memória Ativa**
4. Insira seu conteúdo
5. Toque em **Salvar**

O modelo também pode atualizar a memória ativa via chamadas de ferramentas se **Acessar Memória Ativa** estiver ativado.

---

### Memórias Salvas

Uma coleção de arquivos de memória nomeados que o modelo pode buscar, ler, criar, editar e excluir. Diferente da Memória Ativa (sempre enviada), as memórias salvas são recuperadas sob demanda.

**Use Memórias Salvas para:**
- Material de referência (documentação de API, detalhes de configuração, comandos)
- Notas específicas de projetos
- Aprendizados e percepções de conversas passadas
- Qualquer coisa que você queira que o modelo lembre quando relevante

#### Criando Memórias Manualmente

1. Vá em **Configurações → Memória**
2. Toque em **Adicionar Memória**
3. Insira:
    - **Título** — nome descritivo
    - **Descrição** — resumo breve (usado para correspondência na busca)
    - **Conteúdo** — o conteúdo completo da memória
4. Toque em **Criar**

#### Memórias Criadas pelo Modelo

Quando **Acessar Memórias Salvas** está ativado, o modelo pode criar, ler, atualizar e excluir arquivos de memória via chamadas de ferramentas. Isso permite ao modelo:

- Lembrar fatos que você conta a ele
- Salvar trechos de código úteis ou configurações
- Construir uma base de conhecimento ao longo do tempo
- Limpar informações desatualizadas

---

## Permissões de Memória

Controle o que o modelo pode acessar:

| Configuração | Local | Quando Ativar |
|--------------|-------|---------------|
| **Acessar Memórias Salvas** | Configurações → Memória | Você quer que o modelo leia/escreva arquivos de memória |
| **Acessar Memória Ativa** | Configurações → Memória | Você quer que o modelo atualize o contexto persistente |
| **Acessar Conversas Passadas** | Configurações → Busca em Conversas | Você quer que o modelo pesquise o histórico de chat |

Todos os três são **desativados** por padrão. Ative apenas o que você precisa.

---

## Cache Automático

O cache automático gera embeddings automaticamente para novas mensagens conforme elas chegam. Isso mantém seu índice de busca em conversas atualizado sem intervenção manual.

### Ativar Cache Automático

1. Vá em **Configurações → Busca em Conversas**
2. Escolha um modelo de embedding (se ainda não tiver — consulte [Embedding / RAG](embedding.md))
3. Em **Cache**, ative **Cache automático para novas mensagens**

Quando ativado, cada nova mensagem (usuário e modelo) é automaticamente incorporada e indexada para busca semântica.

### Cache Manual

Se o cache automático estiver desativado, você pode armazenar mensagens em cache manualmente:

1. Vá em **Configurações → Busca em Conversas**
2. Toque em **Cache** — calcula embeddings para todas as mensagens não armazenadas
3. O progresso é exibido como um indicador circular

Toque em **Re-cache** para reconstruir todo o índice do zero. Isso exclui todos os embeddings em cache e reprocessa cada mensagem. Use quando:
- Você mudou de modelo de embedding
- O cache parece corrompido ou desatualizado
- Os resultados da busca estão inesperadamente ruins

!!! warning
    O re-cache é irreversível e pode levar um tempo dependendo da quantidade de mensagens e da velocidade do modelo de embedding.

### Status do Cache

As configurações do modelo de embedding mostram quantas mensagens estão em cache vs. não armazenadas:
- **"Todas as N mensagens em cache"** — atualizado
- **"X de Y mensagens não armazenadas"** — pendências a processar

---

## Chamadas de Ferramentas de Memória no Chat

Quando o modelo usa ferramentas de memória, você verá cards inline:

| Ferramenta | Texto do Card |
|------------|---------------|
| Buscar | "Procurou em N memórias salvas" |
| Ler | "Leu [nome da memória]" |
| Salvar | "Salvou [nome da memória]" |
| Editar | "Atualizou [nome da memória]" |
| Excluir | "Removeu [nome da memória]" |
| Atualizar Ativa | "Atualizou a memória ativa" |

Toque em qualquer card para ver o conteúdo completo que foi lido ou escrito.

---

## Melhores Práticas

- **Mantenha a Memória Ativa concisa** — ela é incluída em cada chamada de API, então conteúdo extenso desperdiça tokens
- **Use títulos descritivos para Memórias Salvas** — os títulos ajudam o modelo a encontrar a memória certa
- **Ative o cache automático** se você usa busca em conversas regularmente
- **Faça re-cache após trocar de modelo de embedding** — modelos diferentes produzem embeddings incompatíveis

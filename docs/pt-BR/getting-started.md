# Primeiros Passos

Este guia orienta você na instalação do 橘子岛, adição da sua primeira chave de API e envio da sua primeira mensagem.

## Instalação

### Pelo F-Droid (Recomendado)

O 橘子岛 está disponível no F-Droid, a loja de aplicativos Android de código aberto.

1. Instale o [F-Droid](https://f-droid.org/) no seu dispositivo
2. Abra o F-Droid, pesquise por **橘子岛**
3. Toque em **Instalar**

### Pelas Releases do GitHub

1. Visite a [página de Releases](https://github.com/chloemeadow0-code/Orange-Island/releases)
2. Baixe o arquivo `.apk` mais recente
3. Abra o arquivo no seu dispositivo e confirme a instalação quando solicitado

### Compilar a partir do Código Fonte

Se preferir compilar você mesmo:

1. Clone o repositório:
   ```
   git clone https://github.com/chloemeadow0-code/Orange-Island.git
   ```
2. Abra o projeto no [Android Studio](https://developer.android.com/studio) (Ladybug ou mais recente)
3. Sincronize o Gradle e compile

Requisitos: Android SDK 34+, JDK 17+.

---

## Primeira Execução

Ao abrir o 橘子岛 pela primeira vez, você verá uma tela de boas-vindas com um campo de texto. Antes de poder conversar, você precisa configurar um provedor e uma chave de API.

### Passo 1: Adicionar uma Chave de API

1. Toque no ícone de **Configurações** (engrenagem no canto inferior direito) na barra de navegação
2. Em **Serviços**, toque em **Provedor**
3. Selecione um provedor da lista (ex.: **OpenAI**, **Anthropic**, **Google**)
4. Toque em **Adicionar Nova Chave**
5. Insira um nome para sua chave (ex.: "Pessoal") e cole sua chave de API
6. Toque em **Adicionar**

??? tip "Onde obtenho uma chave de API?"
    - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — nível gratuito disponível
    - **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
    - **Anthropic**: [Console API Keys](https://console.anthropic.com/)
    - **DeepSeek**: [Platform](https://platform.deepseek.com/)
    - **OpenRouter**: [Página de Keys](https://openrouter.ai/keys)

    Consulte a página [Provedores de API](provider.md) para detalhes sobre cada provedor.

### Passo 2: Sincronizar Modelos

1. Volte para Configurações e toque em **Modelos** (em **Serviços**)
2. Toque em **Sincronizar de Todos os Provedores**
3. O 橘子岛 busca a lista mais recente de modelos para todos os provedores configurados
4. Após sincronizar, toque em um modelo para defini-lo como seu **Modelo Padrão**

### Passo 3: Enviar Sua Primeira Mensagem

1. Toque na **seta para voltar** para retornar à tela de chat
2. Digite uma mensagem no campo de entrada na parte inferior
3. Toque em **Enviar** (ícone de avião de papel)

O modelo transmitirá sua resposta em tempo real.

---

## Layout do Aplicativo

O 橘子岛 tem um layout limpo centrado na tela de chat:

### Barra Superior

- **Título da conversa** — exibe o nome da conversa atual (toque para renomear)
- **Menu hambúrguer** (:material-menu:) — abre a gaveta de conversas
- **Menu overflow** (:material-dots-vertical:) — configurações por conversa (modelo, prompt de sistema, parâmetros de geração)

### Gaveta de Conversas

Toque no **menu hambúrguer** ou deslize para a direita a partir da borda esquerda para abrir:

- **Barra de busca** — encontre conversas passadas por palavra-chave ou busca semântica
- **Lista de conversas** — todas as conversas, mais recentes primeiro
- **Configurações** (:material-cog:) — configure provedores, modelos, prompts e mais
- **Nova Conversa** — inicie uma conversa nova

### Tela de Chat

- **Área de mensagens** — histórico de conversa rolável com renderização markdown
- **Barra inferior** — entrada de texto, seletor de modelo, botão de anexo (+) e botão de envio

---

## Próximos Passos

- [Configure prompts de sistema](system-prompts.md) para personalizar o comportamento do modelo
- [Configure busca na web](web-search.md) para acesso à internet em tempo real
- [Explore as ferramentas agênticas](tools.md) — execução shell, operações de arquivo e memória
- [Importe dados](import-export.md) do Claude ou ChatGPT
- [Execute modelos locais](local-model.md) para uso offline

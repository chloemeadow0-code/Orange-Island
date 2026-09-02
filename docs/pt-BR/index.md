# Manual do Usuário do 橘子岛

Bem-vindo ao manual do usuário do 橘子岛. O 橘子岛 é um cliente LLM BYOK (Bring Your Own Key) para Android com acesso a múltiplos provedores, conversas com ramificação não linear, chamada de ferramentas agênticas e controle remoto de dispositivos.

## Links Rápidos

### Primeiros Passos

- **[Primeiros Passos](getting-started.md)** — instale, configure e envie sua primeira mensagem
- **[FAQ](faq.md)** — respostas para perguntas comuns

### Funcionalidades Principais

- **[Conversas](conversations.md)** — ramificação não linear, operações em mensagens, streaming, renderização markdown
- **[Provedores de API](provider.md)** — conecte-se ao OpenAI, Anthropic, Google, DeepSeek, Ollama e endpoints personalizados
- **[Modelos](models.md)** — ativar/desativar modelos, aliases, sincronização de modelos por provedor
- **[Prompts de Sistema](system-prompts.md)** — editor de três seções, substituição de variáveis, alternância por conversa
- **[Geração](generation.md)** — temperatura, top P, máximo de tokens, thinking, penalidades de frequência/presença
- **[Geração de Títulos](title-generation.md)** — gere títulos de conversas automaticamente
- **[Transcrição de Imagens](transcription.md)** — pipeline de imagem para texto para provedores sem visão
- **[Geração de Imagens](image-generation.md)** — geração de texto para imagem como ferramenta de chat
- **[Aparência](appearance.md)** — modo de tema, esquema de cores, cores dinâmicas, estilo do esquema, efeitos de desfoque

### Ferramentas Agênticas

- **[Visão Geral](tools.md)** — como funciona a chamada de ferramentas em múltiplas rodadas
- **[Busca na Web](web-search.md)** — integração com DuckDuckGo Lite, Brave, Serper, Tavily, SearXNG
- **[Shell Remoto (Conch)](shell.md)** — execução remota criptografada de comandos, operações de arquivo, integração MCP
- **[Sandbox](sandbox.md)** — ambiente Alpine Linux local para execução isolada de comandos

### Gerenciamento de Conhecimento

- **[Busca em Conversas](search.md)** — busca por palavra-chave e semântica (RAG) no histórico de chat
- **[Embedding / RAG](embedding.md)** — configure modelos de embedding para recuperação semântica
- **[Memória & Cache](memory.md)** — memória ativa, memórias salvas, cache automático

### Mais

- **[Modelos Locais](local-model.md)** — execute modelos GGUF no dispositivo via llama.cpp
- **[Importação de PDF](pdf-import.md)** — extraia e envie páginas de PDF para modelos de visão
- **[Portabilidade de Dados](import-export.md)** — exporte/importe arquivos .橘子岛, backup automático, importe do Claude e ChatGPT
- **[Idioma](language.md)** — alterne entre English, 中文, 繁體中文 ou padrão do sistema
- **[Sobre](about.md)** — informações da versão, atualizações, opções de documentação, links, avaliação

---

## Sobre o 橘子岛

O 橘子岛 é um cliente Android BYOK para usuários avançados de IA:

- **Sem intermediários**: Conexões diretas à API, sem telemetria, sem rastreamento
- **Armazenamento no dispositivo**: Tudo vive localmente em um banco de dados Room
- **Conversas não lineares**: Edite qualquer mensagem passada e explore ramificações alternativas
- **Agêntico por padrão**: Chamada de ferramentas em múltiplas rodadas com busca na web, execução shell, operações de arquivo e memória
- **Controle remoto**: Gerencie servidores via o protocolo criptografado Conch
- **Código aberto**: Licenciado sob MIT, [código fonte no GitHub](https://github.com/chloemeadow0-code/Orange-Island)

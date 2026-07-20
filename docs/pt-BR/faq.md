# Perguntas Frequentes

## API & Provedores

### Como obtenho uma chave de API?

- **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) — nível gratuito disponível
- **OpenAI**: [Platform API Keys](https://platform.openai.com/api-keys)
- **Anthropic**: [Console API Keys](https://console.anthropic.com/)
- **DeepSeek**: [Platform](https://platform.deepseek.com/)
- **OpenRouter**: [Página de Keys](https://openrouter.ai/keys)
- **Brave Search**: [Brave Search API](https://api.search.brave.com/)

### Posso usar múltiplas chaves de API para o mesmo provedor?

Sim. Cada provedor suporta múltiplas chaves nomeadas. Toque no botão de rádio para selecionar a chave ativa. Útil para alternar entre chaves de trabalho/pessoais ou ter uma chave reserva pronta. Consulte [Provedores de API](provider.md#api-keys).

### Como adiciono um provedor personalizado?

Vá em Configurações → Provedor → **+ Adicionar Provedor Personalizado**. Insira um nome e uma URL base. Qualquer endpoint compatível com OpenAI funciona. Consulte [Provedores Personalizados](provider.md#custom-providers).

---

## Modelos Locais

### Quais modelos GGUF funcionam?

O 橘子岛 suporta o formato GGUF tanto para chat quanto para embedding. Modelos de chat devem caber na memória do dispositivo (1–8B de parâmetros, dependendo da RAM). Modelos de embedding são bem menores (100–500 MB). Consulte [Modelos Locais](local-model.md).

### Como executo modelos offline?

Importe um modelo GGUF de chat via Configurações → Provedor → Local → **Importar Modelo GGUF**. Para busca semântica totalmente offline, importe também um modelo GGUF de embedding. Nenhuma conexão de rede é necessária.

### Por que meu modelo local está tão lento?

A inferência local roda na CPU do seu dispositivo. É inerentemente mais lenta que APIs na nuvem. Dicas: use modelos menores (1–3B parâmetros), quantização mais baixa (Q4_K_M), janelas de contexto menores e feche aplicativos em segundo plano.

---

## Embeddings & Busca

### Por que o teste do meu modelo de embedding está falhando?

Causas comuns:

- **Nome do modelo errado** — verifique a grafia exata, incluindo tags do Ollama (ex.: `qwen3-embedding:8b` e não `qwen3-embedding`)
- **URL base errada** — certifique-se de que o endpoint suporta `/v1/embeddings`
- **Chave de API ausente** — alguns provedores exigem autenticação até para embeddings
- **Rede** — verifique a conectividade com o endpoint

### Qual a diferença entre busca por palavra-chave e RAG?

A busca por palavra-chave corresponde texto exato. RAG (busca semântica) corresponde por significado — "configuração do banco de dados" pode encontrar "configuração do Room" mesmo sem palavras em comum. RAG requer um modelo de embedding e mensagens em cache. Consulte [Busca em Conversas](search.md).

### Como uso o Ollama para embeddings?

1. Instale o Ollama em uma máquina
2. Baixe um modelo de embedding: `ollama pull qwen3-embedding:8b`
3. No 橘子岛, adicione um modelo de embedding remoto com o preset **Ollama**
4. Use `http://<host>:11434/v1` como URL base
5. Insira o nome exato do modelo incluindo a tag (ex.: `qwen3-embedding:8b`)
6. Deixe a chave de API em branco

---

## Memória

### Qual a diferença entre Memória Ativa e Memórias Salvas?

**Memória Ativa** é um único contexto persistente incluído em **toda chamada de API** — o modelo sempre a vê. **Memórias Salvas** são uma coleção de arquivos nomeados que o modelo busca e recupera sob demanda. Use Memória Ativa para fatos persistentes; use Memórias Salvas para material de referência. Consulte [Memória & Cache](memory.md).

### O modelo pode modificar minhas memórias?

Sim, se você ativar **Acessar Memórias Salvas** e/ou **Acessar Memória Ativa** em Configurações → Memória. O modelo pode criar, ler, editar e excluir memórias via chamadas de ferramentas. Todas as permissões são desativadas por padrão.

---

## Shell & Ferramentas

### Como configuro o acesso ao shell remoto?

Implante o servidor [Conch](https://github.com/orangeisland/conch) na máquina de destino e adicione o dispositivo em Configurações → Shell com sua URL e chave de API. Consulte [Shell Remoto](shell.md).

### A conexão do shell é criptografada?

Sim. O Conch usa troca de chaves ECDH + criptografia AES-256-GCM + assinatura HMAC-SHA256. Todo o tráfego entre o 橘子岛 e o servidor Conch é criptografado de ponta a ponta.

---

## Dados

### Como faço backup dos meus dados?

Vá em Configurações → Controle de Dados → **Exportar Dados**. Selecione as categorias e exporte para um arquivo `.橘子岛`. Armazene-o em um local seguro. Consulte [Portabilidade de Dados](import-export.md).

### Posso importar do ChatGPT ou Claude?

Sim. Exporte seus dados do ChatGPT ou Claude (eles fornecem arquivos `.zip`) e importe em Configurações → Controle de Dados → **Terceiros**. As estratégias Mesclar e Substituir são suportadas. Consulte [Portabilidade de Dados](import-export.md#third-party-import).

### Minhas chaves de API são incluídas nas exportações?

Elas podem ser, mas é opcional. A tela de exportação permite ativar ou desativar a inclusão de chaves de API. Um aviso é exibido quando você ativa essa opção. As chaves são armazenadas em texto puro dentro do arquivo `.橘子岛`, então inclua-as apenas para migrações completas de dispositivo para destinos confiáveis.

---

## Geral

### Onde meus dados são armazenados?

Tudo é armazenado localmente no seu dispositivo Android em um banco de dados Room. O 橘子岛 não tem servidores, nem sincronização na nuvem, nem telemetria. As mensagens são enviadas diretamente do seu dispositivo para o provedor de IA que você configurar.

### O 橘子岛 suporta vários idiomas?

Sim. A interface do aplicativo suporta **English** e **中文 (Chinês)**. Configurações → Idioma. É necessário reiniciar após a troca.

### Como relato um bug ou solicito uma funcionalidade?

Abra uma issue no [GitHub](https://github.com/orangeisland/app/issues). Para contribuições, consulte a seção [Contributing](https://github.com/orangeisland/app#contributing) do README.

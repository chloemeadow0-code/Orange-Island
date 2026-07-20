# Provedores de API

O 橘子岛 conecta-se diretamente aos provedores de IA — sem intermediários, sem assinatura, sem telemetria. Você traz suas próprias chaves de API e tudo roda a partir do seu dispositivo.

## Provedores Integrados

| Provedor | URL Base | Modelos | Observações |
|----------|----------|---------|-------------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Série Gemini | Nível gratuito disponível via Google AI Studio |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4, GPT-4o, série o | Modelos de raciocínio suportados |
| **Anthropic** | `https://api.anthropic.com/v1` | Série Claude | Pensamento estendido suportado |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3, DeepSeek-R1 | Modelos de raciocínio suportados |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Série Qwen | Via Alibaba DashScope |
| **Ollama** | `http://localhost:11434/v1` | Qualquer modelo baixado | Auto-hospedado, sem necessidade de chave de API |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Múltiplos provedores | Acesse muitos modelos através de uma única API |
| **Local** | N/A | Modelos GGUF | No dispositivo via llama.cpp, totalmente offline |

## Alternar Provedores

Toque no seletor de provedor em Configurações para alternar entre provedores. Cada provedor mantém seus próprios:

- Chaves de API
- URL base (editável para proxies/auto-hospedados)
- Lista de modelos

---

## Chaves de API

### Múltiplas Chaves por Provedor

Cada provedor suporta múltiplas chaves de API nomeadas. Isso permite:

- **Rotação** — alterne entre chaves para diferentes níveis de uso
- **Organização** — separe o uso profissional do pessoal
- **Reserva** — mantenha uma chave de backup pronta

### Gerenciando Chaves

1. Vá em **Configurações → Provedor**
2. Selecione um provedor
3. Em **Chaves de API**, toque em **Adicionar Nova Chave**
4. Insira um **nome** (ex.: "Trabalho", "Pessoal", "Compartilhado da Equipe") e o **valor da chave**
5. Toque em **Adicionar**

Toque no botão de rádio para definir a chave ativa. Pressione longamente uma chave para **Editar** ou **Excluir**.

### Segurança das Chaves

!!! warning
    As chaves de API são armazenadas localmente em um banco de dados Room criptografado. Elas nunca são enviadas para servidores do 橘子岛 (não existem servidores). No entanto, elas são exportadas em texto puro se você incluí-las em um arquivo de exportação `.橘子岛`.

---

## Provedores Personalizados

Adicione qualquer endpoint de API compatível com OpenAI:

1. Vá em **Configurações → Provedor**
2. Toque em **+ Adicionar Provedor Personalizado** na parte inferior da lista de provedores
3. Insira:
    - **Nome do Provedor** — qualquer nome de exibição
    - **URL Base** — o endpoint da API
4. Toque em **Adicionar**

O 橘子岛 busca a lista de modelos em `{base_url}/v1/models`. Uma vez adicionados, os provedores personalizados funcionam exatamente como os integrados: adicione chaves de API, sincronize modelos e converse.

### Casos de Uso

- **Auto-hospedado** — conecte-se ao vLLM, LocalAI, text-generation-webui ou outros servidores compatíveis com OpenAI
- **Proxies** — roteie através de um proxy corporativo ou gateway de API
- **Endpoints alternativos** — use Azure OpenAI, Cloudflare AI Gateway ou outros serviços compatíveis

### Renomear ou Excluir

Pressione longamente um provedor personalizado para **Renomear** ou **Excluir**. Excluir remove o provedor e todas as suas chaves.

!!! warning
    Provedores integrados não podem ser renomeados ou excluídos.

---

## Substituição da URL Base

Todo provedor (incluindo os integrados) tem uma **URL Base** editável. Isso é útil para:

- **Proxies**: Rotear através de `https://meu-proxy.exemplo.com/v1`
- **Auto-hospedado**: Apontar para sua própria instância
- **Roteamento regional**: Usar endpoints específicos da região

---

## Sincronizando Modelos

Após adicionar chaves de API, sincronize a lista de modelos:

1. Vá em **Configurações → Modelos**
2. Toque em **Sincronizar de Todos os Provedores**
3. O 橘子岛 busca os modelos disponíveis de cada provedor configurado

Uma snackbar mostra o progresso e os resultados da sincronização. Você pode então ativar/desativar modelos individuais e definir um padrão.

---

## Notas Específicas por Provedor

### Google Gemini

- Chaves de API do [Google AI Studio](https://aistudio.google.com/apikey)
- Nível gratuito disponível com limites de taxa
- Suporta execução de código e search grounding (ferramentas integradas)

### OpenAI

- Chaves de API da [Platform](https://platform.openai.com/api-keys)
- Modelos de raciocínio (o1, o3) exigem acesso específico à API
- Streaming, ferramentas e visão são suportados

### Anthropic

- Chaves de API do [Console](https://console.anthropic.com/)
- Pensamento estendido com orçamentos de tokens configuráveis
- Uso de ferramentas com chamadas paralelas suportado

### Ollama

- Nenhuma chave de API necessária (rede local)
- URL base tipicamente `http://<host>:11434/v1`
- Lista de modelos obtida da API do Ollama
- Consulte [FAQ](faq.md) para solução de problemas específicos do Ollama

### OpenRouter

- Chave de API única para mais de 200 modelos
- Preço por token varia conforme o modelo
- Bom para experimentar modelos diferentes sem contas individuais em cada provedor

### Local (llama.cpp)

- Nenhuma rede necessária
- Arquivos de modelo GGUF armazenados no dispositivo
- Consulte [Modelos Locais](local-model.md) para configuração

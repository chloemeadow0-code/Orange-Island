# Busca na Web

Permita que o modelo pesquise na internet e busque páginas da web em tempo real. Quando ativado, o modelo pode consultar informações atuais, verificar fatos, recuperar documentação ou pesquisar tópicos — tudo de forma autônoma via chamada de ferramentas.

## Provedores Suportados

| Provedor | Descrição | Nível Gratuito | Configuração |
|----------|-----------|----------------|--------------|
| **DuckDuckGo Lite** | Anônimo, sem necessidade de chave de API | Sim (ilimitado, melhor esforço) | Sem configuração — funciona imediatamente |
| **Brave** | API de busca focada em privacidade | Sim (2.000 consultas/mês) | [api.search.brave.com](https://api.search.brave.com/) |
| **Serper** | API rápida de busca do Google | Sim (2.500 consultas/mês) | [serper.dev](https://serper.dev) |
| **Tavily** | Busca otimizada para IA, feita para agentes LLM | Sim (1.000 consultas/mês) | [tavily.com](https://tavily.com) |
| **SearXNG** | Motor de metabusca auto-hospedado | Auto-hospedado (ilimitado) | Sua própria instância |

## Configuração

### DuckDuckGo Lite

DuckDuckGo Lite é o provedor de busca **padrão** — sem necessidade de chave de API, funciona imediatamente.

1. No 橘子岛, vá em **Configurações → Busca na Web**
2. Selecione **DuckDuckGo Lite** como provedor de busca
3. Nenhuma chave ou URL necessária — comece a buscar 橘子岛 mesmo

!!! note "Serviço de melhor esforço"
    DuckDuckGo Lite usa raspagem HTML do `lite.duckduckgo.com`. O DDG pode alterar seu layout, limitar a taxa ou bloquear requisições automatizadas. Ele é fornecido como uma opção explicitamente de melhor esforço e sem chave. Se você precisa de confiabilidade, configure um dos provedores baseados em API abaixo.

### Brave

1. Obtenha uma chave de API em [Brave Search API](https://api.search.brave.com/)
2. No 橘子岛, vá em **Configurações → Busca na Web**
3. Selecione **Brave** como provedor de busca
4. Cole sua chave de API

### Serper

1. Obtenha uma chave de API em [serper.dev](https://serper.dev)
2. No 橘子岛, vá em **Configurações → Busca na Web**
3. Selecione **Serper**
4. Cole sua chave de API

### Tavily

1. Obtenha uma chave de API em [tavily.com](https://tavily.com)
2. No 橘子岛, vá em **Configurações → Busca na Web**
3. Selecione **Tavily**
4. Cole sua chave de API

### SearXNG

1. Configure uma instância SearXNG (auto-hospedada) ou use uma instância pública
2. No 橘子岛, vá em **Configurações → Busca na Web**
3. Selecione **SearXNG**
4. Insira a **URL Base** da sua instância (ex.: `https://searx.be`)
5. A chave de API é opcional (necessária apenas se sua instância exigir autenticação)

!!! warning "Instâncias Públicas"
    Instâncias públicas do SearXNG frequentemente têm limites de taxa ou são não confiáveis. A auto-hospedagem é recomendada para uso consistente.

---

## Configuração

### Máximo de Resultados

Defina quantos resultados de busca buscar por consulta: **1–10**. O padrão depende do dispositivo. Mais resultados dão mais contexto ao modelo, mas custam mais tokens.

### Ativar/Desativar

Alterne **Ativar Busca na Web** na página de configurações de Busca na Web. Quando desativado, o modelo não pode chamar a ferramenta de busca na web.

---

## Como o Modelo Usa a Busca

Quando você faz uma pergunta que precisa de informações atuais ou externas, o modelo automaticamente chama a busca na web:

1. **Buscar**: O modelo chama a API de busca com uma consulta que ele formula
2. **Obter**: O modelo pode opcionalmente buscar o conteúdo completo das páginas a partir das URLs dos resultados
3. **Sintetizar**: O modelo lê os resultados e os integra em sua resposta

Você verá cada busca e obtenção como cards de ferramenta inline na conversa.

### Exemplo

```text
Você: "Qual é a versão mais recente do Python?"
Modelo: [Busca "versão mais recente do Python 2026"]
        [Lê o resultado]
        "Python 3.14.0 foi lançado em outubro de 2025..."
```

---

## Obtenção de Páginas Web

Além da busca, o modelo pode buscar e ler páginas web específicas. Quando o modelo encontra uma URL nos resultados da busca, ele pode chamar `web_fetch` para recuperar o conteúdo completo da página:

- O conteúdo obtido é convertido para markdown
- O modelo processa e extrai informações relevantes
- Os resultados da obtenção são mostrados como cards de ferramenta

---

## Considerações de Privacidade

Ao usar a busca na web:

- Suas consultas vão para o provedor de busca (Brave, Serper, etc.), não para o 橘子岛
- O 橘子岛 não registra nem armazena suas consultas de busca (exceto na própria conversa)
- A auto-hospedagem com SearXNG oferece a maior privacidade — as consultas permanecem na sua infraestrutura

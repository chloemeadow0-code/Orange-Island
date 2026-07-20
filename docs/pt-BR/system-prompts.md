# Prompts de Sistema

Os prompts de sistema definem a persona, o comportamento e as regras básicas do modelo. O 橘子岛 oferece controle refinado sobre como as instruções são montadas e enviadas ao modelo.

## Editor de Três Seções

Cada modelo de prompt de sistema tem três seções editáveis independentemente:

```text
┌─────────────────────────────────┐
│ Prompt de Sistema               │ ← Instruções principais (persona, regras, tom)
├─────────────────────────────────┤
│ Prefixo do Usuário              │ ← Adicionado antes de cada mensagem do usuário
├─────────────────────────────────┤
│ Sufixo do Usuário               │ ← Adicionado após cada mensagem do usuário
└─────────────────────────────────┘
```

### Prompt de Sistema

O bloco de instruções principal. É aqui que você define:

- **Persona**: "Você é um desenvolvedor Python sênior focado em arquitetura limpa."
- **Regras**: "Sempre responda em chinês. Use marcadores para listas."
- **Restrições**: "Nunca peça desculpas. Seja conciso. Prefira código a explicações."

### Prefixo e Sufixo do Usuário

Estes envolvem cada mensagem que você envia:

- **Prefixo do Usuário** — adicionado antes do texto da sua mensagem. Útil para lembretes ou tags de contexto.
- **Sufixo do Usuário** — adicionado após o texto da sua mensagem. Útil para instruções de fechamento.

**Exemplo**: Se seu prefixo for `[Contexto: trabalhando na documentação do 橘子岛]` e o sufixo for `\n\nPor favor, responda em Markdown.`, o modelo recebe:

```text
[Contexto: trabalhando na documentação do 橘子岛]
Como configuro a busca na web?
Por favor, responda em Markdown.
```

---

## Criando um Prompt

1. Vá em **Configurações → Prompts de Sistema**
2. Toque em **Adicionar Novo Prompt**
3. Insira um **título** (ex.: "Tradutor", "Revisor de Código", "Assistente Chinês")
4. Preencha as três seções:
    - Toque em **Adicionar Texto** para escrever conteúdo estático
    - Toque em **Adicionar Variável** para inserir valores dinâmicos
5. Toque em **Salvar**

### Reordenando Itens

Dentro de cada seção, você pode ter múltiplos blocos de texto e variáveis. Pressione longamente um item para:

- **Mover para cima** / **Mover para baixo** — reordenar dentro da seção
- **Remover** — excluir o item

---

## Substituição de Variáveis

As variáveis são substituídas por valores dinâmicos quando a mensagem é enviada:

| Variável | Expande para | Exemplo | Quando Resolvida |
|----------|-------------|---------|------------------|
| `{time}` | Hora atual (HH:mm:ss) | `14:30:00` | Compilação do prompt |
| `{date}` | Data atual (YYYY-MM-DD) | `2026-05-10` | Compilação do prompt |
| `{sent_time}` | Hora de envio da mensagem (HH:mm) | `10:05` | Por mensagem |
| `{sent_date}` | Data de envio da mensagem (YYYY-MM-DD) | `2026-05-11` | Por mensagem |
| `{active_memory}` | Conteúdo da memória ativa | `[Seu conteúdo de memória salvo]` | Compilação do prompt |
| `{model_id}` | ID do modelo atualmente selecionado | `gemini-1.5-flash` | Compilação do prompt |

**Variáveis por mensagem** (`{sent_time}`, `{sent_date}`) são resolvidas cada vez que você envia uma mensagem, refletindo o horário exato de envio. **Variáveis de nível de prompt** (`{time}`, `{date}`, `{active_memory}`, `{model_id}`) são resolvidas quando o prompt de sistema é compilado.

!!! tip
    Use `{sent_date}` para prompts sensíveis à data como "Hoje é {sent_date}. Ao discutir eventos recentes, note que seu conhecimento pode estar desatualizado." Use `{active_memory}` para injetar a memória persistente do modelo nas instruções do sistema.

### Adicionando uma Variável

1. Em qualquer seção do editor, toque em **Adicionar Variável**
2. Selecione a variável do seletor
3. Ela aparece como um chip/pílula na seção — arraste para reposicionar

---

## Gerenciando Prompts

### Definir como Padrão

Toque no botão de rádio ao lado de um prompt para torná-lo o **padrão global**. Todas as conversas usam este prompt, a menos que substituído.

### Substituição por Conversa

Cada conversa pode usar um prompt de sistema diferente:

1. Abra uma conversa
2. Toque no menu overflow (:material-dots-vertical:) na barra superior
3. Selecione **Prompt da Conversa**
4. Escolha um prompt da lista

A configuração por conversa substitui o padrão global apenas para essa conversa.

### Editar ou Excluir

- Toque em um prompt para **editá-lo**
- Pressione longamente e selecione **Excluir** para removê-lo

!!! warning
    Excluir um prompt de sistema é permanente. As conversas que o usavam voltarão ao padrão global.

---

## Sem Prompt de Sistema

Se nenhum prompt de sistema estiver selecionado, o modelo não recebe instruções especiais — ele se comporta de acordo com seu treinamento base. Isso às vezes é desejável para testes ou para modelos que têm melhor desempenho sem instruções de sistema.

Para não usar nenhum prompt, selecione **Nenhum** na lista de prompts.

---

## Geração Automática de Títulos

O 橘子岛 pode gerar automaticamente títulos de conversa após a primeira resposta:

1. Vá em **Configurações → Geração de Títulos**
2. Ative **Gerar Título Automaticamente**
3. Escolha um **Modelo de Título**:
    - **Usar Modelo Atual** — usa qualquer modelo que esteja ativo na conversa
    - **Selecionar Modelo de Título** — escolha um modelo rápido/barato específico para geração de títulos

Quando ativado, uma breve snackbar "Gerando título..." aparece após a primeira resposta do modelo, e a conversa é automaticamente renomeada de "Sem título" para um título descritivo.

---

## Exemplos de Prompts

### Tradutor

```yaml
Prompt de Sistema: |
  Você é um tradutor profissional. Traduza a entrada do usuário para o inglês.
  Preserve a formatação, blocos de código e termos técnicos. Não adicione explicações.
```

### Revisor de Código

```yaml
Prompt de Sistema: |
  Você é um revisor de código sênior. Ao ver código:
  1. Identifique bugs e casos extremos
  2. Sugira melhorias de desempenho
  3. Verifique problemas de segurança
  Seja específico. Referencie números de linha quando possível.
```

### Assistente Chinês

```yaml
Prompt de Sistema: |
  你是一个乐于助人的中文助手。用简洁、清晰的中文回答问题。
Sufixo do Usuário: |
  \n\n请用中文回答。
```

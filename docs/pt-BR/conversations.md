# Conversas

O sistema de conversas do 橘子岛 é construído em torno de **ramificação não linear** — diferente da maioria dos aplicativos de chat, você pode editar qualquer mensagem passada e explorar caminhos alternativos de resposta sem perder a conversa original.

## Criando Conversas

Toque em **Nova Conversa** na gaveta de conversas ou simplesmente comece a digitar na tela de chat. Uma nova conversa é criada automaticamente com sua primeira mensagem.

As conversas recebem títulos automáticos após a primeira resposta (se a [Geração de Títulos](system-prompts.md#auto-title-generation) estiver ativada), ou você pode renomeá-las manualmente.

## Gerenciando Conversas

### Alternar Conversas

Abra a **gaveta de conversas** (menu hambúrguer :material-menu: ou deslize para a direita) e toque em qualquer conversa para abri-la.

### Renomear

1. Pressione longamente uma conversa na gaveta
2. Toque em **Renomear**
3. Insira um novo título e salve

### Excluir

1. Pressione longamente uma conversa na gaveta
2. Toque em **Excluir**
3. Confirme a exclusão — esta ação não pode ser desfeita

---

## Ramificação Não Linear

Esta é a funcionalidade assinatura do 橘子岛. Cada mensagem pode ser um ponto de ramificação.

### Editando uma Mensagem Passada

1. Pressione longamente qualquer balão de mensagem (usuário ou modelo)
2. Toque em **Editar**
3. Modifique o conteúdo da mensagem
4. Envie — o 橘子岛 cria uma **nova ramificação** a partir deste ponto

A ramificação original é preservada. Você pode alternar entre ramificações a qualquer momento.

### Como as Ramificações Funcionam

Cada mensagem vive em uma **estrutura de árvore**:

```text
Mensagem 1 (Usuário)
├── Mensagem 2 (Modelo) ← resposta original
└── Mensagem 3 (Modelo) ← ramificação criada após editar a Mensagem 1
    ├── Mensagem 4 (Usuário)
    └── ...
```

Quando você edita uma mensagem e regenera, a nova resposta se torna uma irmã da original — ambas existem sob a mesma mensagem pai.

### Alternando Ramificações

Quando uma mensagem tem múltiplos filhos (ramificações), a interface mostra controles de navegação para alternar entre eles. Você pode explorar caminhos alternativos sem perder o contexto.

### Por que Ramificar?

- **Explorar alternativas** — faça a mesma pergunta com palavras diferentes
- **Teste A/B de prompts** — compare respostas de diferentes prompts de sistema ou modelos
- **Corrigir erros** — corrija um erro de digitação na sua pergunta sem perder o fio original
- **Iterar** — refine um prompt através de múltiplas versões mantendo todas as tentativas

---

## Operações em Mensagens

Pressione longamente qualquer mensagem para acessar estas ações:

| Ação | Descrição |
|------|-----------|
| **Copiar** | Copia o texto da mensagem para a área de transferência |
| **Editar** | Edita a mensagem e cria uma ramificação |
| **Info** | Exibe metadados: timestamp, modelo usado, contagem de tokens |
| **Excluir** | Exclui esta mensagem e todas as respostas seguintes |

!!! warning "Excluindo uma Mensagem"
    Excluir uma mensagem também remove todas as respostas que a seguem. Isso não pode ser desfeito.

---

## A Barra Inferior

A área de entrada do chat fornece acesso rápido a controles essenciais:

### Seletor de Modelo

Toque no nome do modelo no lado esquerdo da barra inferior para abrir o **seletor de modelos**. Você pode alternar modelos a qualquer momento — até mesmo no meio da conversa. Mensagens diferentes na mesma conversa podem usar modelos diferentes.

### Anexos

Toque em **+** (:material-plus:) para anexar arquivos:

- **Fotos** — imagens da sua galeria
- **Vídeos** — arquivos de vídeo (com suporte a extração de quadros)
- **Arquivos** — qualquer tipo de arquivo, incluindo PDFs

Os formatos de imagem suportados são enviados diretamente para modelos com capacidade de visão. Arquivos PDF abrem um diálogo de seleção de páginas.

### Enviando

Digite sua mensagem e toque em **Enviar** (:material-send:). O modelo transmite sua resposta token por token.

---

## Streaming & Exibição

### Streaming em Tempo Real

As respostas aparecem palavra por palavra conforme o modelo as gera. O 橘子岛 rola automaticamente para manter o conteúdo mais recente visível. Toque no botão **rolar para o final** (aparece quando você rola para cima) para voltar à resposta ao vivo.

### Renderização Markdown

As respostas do modelo são renderizadas com suporte completo a markdown:

- **Cabeçalhos**, **negrito**, *itálico*, `código inline`
- **Blocos de código** com destaque de sintaxe (use ````` ``` `````)
- **Tabelas**, citações em bloco, listas
- **Matemática LaTeX** — inline `$E=mc^2$` e em bloco `$$\int_a^b f(x)dx$$`

### Exibição de Pensamento

Para modelos que suportam raciocínio (série o da OpenAI, pensamento estendido da Anthropic, pensamento do Gemini, DeepSeek-R1), o processo de pensamento do modelo é mostrado em um **painel recolhível** antes da resposta final:

- O painel mostra "Pensando..." durante a fase de raciocínio
- Quando concluído, exibe a duração do pensamento (ex.: "Pensou por 12s")
- Toque para expandir/recolher o conteúdo do pensamento
- Chamadas de ferramentas feitas durante o pensamento são contadas (ex.: "Pensou por 8s, chamou 2 ferramentas")

---

## Configurações por Conversa

Cada conversa pode substituir os padrões globais:

- **Modelo** — selecione um modelo diferente para esta conversa
- **Prompt de Sistema** — use uma instrução de sistema diferente
- **Parâmetros de geração** — temperatura, máximo de tokens, nível de thinking

Essas substituições são definidas no menu overflow da conversa na barra superior.

---

## Janela de Contexto

O 橘子岛 rastreia o uso de tokens em tempo real. Quando uma conversa excede a janela de contexto do modelo, as mensagens mais antigas ficam visualmente **esmaecidas** para indicar que estão fora do contexto ativo. O modelo não "vê" mais as mensagens esmaecidas, mas elas permanecem visíveis na sua interface.

Ajuste o tamanho da janela de contexto em **Configurações → Geração → Janela de Contexto**.

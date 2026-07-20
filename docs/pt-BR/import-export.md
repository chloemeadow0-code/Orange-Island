# Portabilidade de Dados

O 橘子岛 armazena todos os seus dados no dispositivo e oferece capacidades completas de importação/exportação. Você é dono dos seus dados — mova-os para dentro, mova-os para fora, faça backup.

## Exportação

Exporte seus dados para um único arquivo `.橘子岛` — um arquivo portátil que contém tudo que o 橘子岛 armazena.

### O Que É Exportado

Você escolhe o que incluir:

| Categoria | Conteúdo |
|-----------|----------|
| **Conversas & Mensagens** | Todo o histórico de chat, árvores de mensagens, ramificações |
| **Memórias** | Memória ativa e todos os arquivos de memória salvos |
| **Prompts de Sistema** | Todos os modelos de prompt de sistema personalizados |
| **Configurações** | Configuração e preferências do aplicativo |
| **Chaves de API** | Todas as chaves de API configuradas |

!!! danger "Aviso sobre Chaves de API"
    As chaves de API são exportadas em **texto puro**. Qualquer pessoa com o arquivo `.橘子岛` pode ler suas chaves. Ative a exportação de chaves de API apenas se você confiar no destino e manusear o arquivo com segurança.

### Como Exportar

1. Vá em **Configurações → Controle de Dados**
2. Toque em **Exportar Dados**
3. Selecione quais categorias incluir
4. Toque em **Exportar**
5. Escolha onde salvar o arquivo `.橘子岛`

---

## Importação

Restaure dados de uma exportação `.橘子岛` anterior.

### Estratégias de Importação

Ao importar, você escolhe como o 橘子岛 lida com dados que já existem no seu dispositivo:

| Estratégia | Comportamento |
|------------|---------------|
| **Mesclar** | Adiciona novos itens, mantém os existentes. Se um item com o mesmo ID existir, a versão importada o sobrescreve. |
| **Substituir** | Limpa todos os dados existentes nas categorias selecionadas e então importa. Um recomeço limpo. |
| **Pular** | Importa apenas itens que não têm conflito. Itens existentes não são alterados. |

!!! tip
    Use **Mesclar** para a maioria dos casos — adiciona novos dados com segurança preservando o que já está no seu dispositivo.

### Como Importar

1. Vá em **Configurações → Controle de Dados**
2. Toque em **Importar Dados**
3. Selecione um arquivo `.橘子岛`
4. Revise a prévia da importação — veja o que está no arquivo (data de exportação, versão, contagens de conteúdo)
5. Escolha uma estratégia de importação
6. Toque em **Importar**

!!! danger "Aviso sobre Chaves de API"
    Se o arquivo de exportação contiver chaves de API, o 橘子岛 avisa antes de importar. As chaves são importadas em texto puro. Prossiga apenas se você confiar na origem do arquivo.

---

## Importação de Terceiros

Importe conversas de outras plataformas de chat com IA.

Tanto o Claude quanto o ChatGPT exportam seus dados como um arquivo **`.zip`**. O 橘子岛 importa esse `.zip` diretamente — não é necessário descompactá-lo primeiro, e o 橘子岛 **não** aceita arquivos `.json` soltos.

### Importar do Claude

**1. Exportar do Claude.** Vá em [Claude](https://claude.ai/) → **Configurações → Controles de Dados → Exportar dados**. O Claude prepara o arquivo rapidamente — geralmente em **menos de uma hora** — e envia um link de download por e-mail.

!!! warning "Baixe rapidamente"
    O link de download do Claude **expira rapidamente**. Pegue o `.zip` assim que o e-mail chegar — se você esperar muito, o link fica inválido e você terá que solicitar uma nova exportação.

**2. Importar para o 橘子岛.**

1. Vá em **Configurações → Controle de Dados → Terceiros → Importar do Claude**
2. Selecione o arquivo `.zip` exportado
3. Revise a prévia — veja a contagem de conversas e mensagens
4. Escolha a estratégia **Mesclar** ou **Substituir**
5. Toque em **Importar**

!!! note
    O 橘子岛 lê os dados da conversa diretamente do arquivo `.zip` de exportação do Claude. Os anexos são detectados e mostrados na prévia, mas apenas o texto da mensagem é importado — os arquivos de anexo em si não são.

### Importar do ChatGPT

**1. Exportar do ChatGPT.** Vá em [ChatGPT](https://chatgpt.com/) → **Configurações → Controles de Dados → Exportar dados**. O ChatGPT processa a solicitação e envia um link de download por e-mail quando estiver pronto.

!!! info "Seja paciente"
    A exportação do ChatGPT normalmente leva **1–2 dias** para chegar. Isso é normal — aguarde o e-mail em vez de solicitar novamente.

**2. Importar para o 橘子岛.**

1. Vá em **Configurações → Controle de Dados → Terceiros → Importar do ChatGPT**
2. Selecione o arquivo `.zip` baixado
3. Revise a prévia
4. Escolha a estratégia **Mesclar** ou **Substituir**
5. Toque em **Importar**

!!! note
    Tanto mensagens de usuário quanto de assistente são importadas. Os papéis das mensagens são preservados.

---

## Formato de Arquivo

O arquivo `.橘子岛` é um arquivo baseado em JSON. Se você tem inclinação técnica, pode inspecioná-lo ou processá-lo com ferramentas padrão. O formato é projetado para compatibilidade futura e retroativa.

---

## Backup Automático

O 橘子岛 pode fazer backup automático dos seus dados em uma programação. Você não precisa lembrar de exportar — o 橘子岛 cuida disso para você.

### Como Funciona

- O backup automático é executado periodicamente em segundo plano usando o Android WorkManager
- Quando um backup é devido, o 橘子岛 exporta suas categorias selecionadas para o diretório configurado
- Uma notificação aparece apenas se um backup falhar — backups bem-sucedidos são silenciosos
- Backups antigos são excluídos automaticamente com base nas suas configurações de retenção

### Configuração

1. Vá em **Configurações → Controle de Dados → Backup Automático**
2. Alterne **Backup Automático** ligado/desligado
3. Defina **Fazer backup a cada** — escolha 1 dia, 3 dias, 5 dias, 1 semana ou 1 mês
4. Escolha **Conteúdo da exportação** — selecione quais categorias incluir. Chaves de API **podem** ser incluídas (um aviso é mostrado ao marcar essa caixa) — só ative se o local do backup for privado e seguro. Chaves de API **não** são incluídas por padrão.
5. Defina **Local do backup** — toque para escolher uma pasta (padrão: `Download/橘子岛/Backup`)
6. Alterne **Excluir backups antigos automaticamente** ligado/desligado e defina o período **Excluir após**

!!! info "Restrição de Exclusão Automática"
    O período de exclusão deve ser maior que o período de backup. Por exemplo, se você faz backup toda semana, os backups podem ser excluídos automaticamente após 1 mês ou 1 ano — nunca antes. Isso evita excluir seu único backup antes que um novo seja criado.

!!! note
    O backup automático usa o WorkManager do Android para garantir confiabilidade mesmo se o aplicativo for fechado ou o dispositivo reiniciar. Backups podem ter um pequeno atraso durante o modo Doze para conservar bateria.

---

## Melhores Práticas

- **Exporte regularmente** como backup — mantenha o arquivo em algum lugar seguro
- **Ative o Backup Automático** para proteção programada sem intervenção manual
- **Não inclua chaves de API** em exportações de rotina — ative a exportação de chaves apenas para migrações completas de dispositivo
- **Use Mesclar para importações incrementais** — Substituir é destrutivo
- **Pré-visualize antes de importar** — verifique a data de exportação e as contagens de conteúdo para confirmar que é o arquivo correto

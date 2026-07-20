# Modelos

Gerencie quais modelos de IA estão disponíveis e defina seu modelo padrão para conversas.

## Lista de Modelos

A página de **Modelos** mostra todos os modelos que o 橘子岛 conhece, organizados por provedor:

- **Modelo Padrão** — O modelo usado para novas conversas. Toque para alterar.
- **Modelos Disponíveis** — Expanda cada provedor para ver seus modelos. Ative os que você deseja usar.

### Ativar / Desativar Modelos

Marque ou desmarque a caixa de seleção ao lado de um modelo para alternar sua disponibilidade. Modelos desativados não aparecerão no seletor de modelos nas conversas.

### Renomear Modelos

Toque no ícone de edição (caneta) ao lado de um modelo para dar a ele um alias personalizado. Esse alias aparece em todo o aplicativo em vez do ID técnico do modelo.

### Sincronizar Modelos

Toque em **Sincronizar Modelos** para buscar os modelos mais recentes disponíveis de todos os provedores de API configurados. Isso requer conexão com a internet e chaves de API válidas.

!!! tip "Modelos Locais"
    Modelos locais aparecem na seção do provedor **Local**. Eles são gerenciados separadamente em **Configurações → Provedores → Local**.

---

## Modelo Padrão

O **Modelo Padrão** é usado para todas as novas conversas. Para alterá-lo:

1. Toque na linha do modelo padrão no topo da página de Modelos
2. Selecione um modelo da lista (apenas modelos ativados são exibidos)
3. A alteração tem efeito imediato

Você pode substituir o modelo por conversa a partir do seletor de modelos na tela de chat.

---

## Aliases de Modelos

Os aliases de modelo permitem dar nomes amigáveis a modelos com IDs técnicos longos. Por exemplo, você pode renomear `openai/gpt-4o-mini` para apenas "GPT-4o Mini".

Os aliases são exibidos em todos os lugares: no seletor de modelos, nos cabeçalhos de conversa e nas páginas de configurações.

Para remover um alias, limpe o campo de texto e salve.

---

## Solução de Problemas

### Modelos não aparecem

- Toque em **Sincronizar Modelos** para atualizar a lista
- Verifique se você tem uma chave de API válida para o provedor em **Configurações → Provedores**
- Verifique sua conexão com a internet
- Alguns provedores podem estar temporariamente indisponíveis

### Modelos locais não são exibidos

- Importe um arquivo de modelo GGUF em **Configurações → Provedores → Local**
- O modelo deve estar no formato GGUF válido

# Ferramentas Agênticas

Os modelos do 橘子岛 podem usar ferramentas de forma autônoma — eles decidem o que buscar, executar, ler ou lembrar sem que você precise acionar manualmente cada ação. As ferramentas operam em **loops de múltiplas rodadas**: o modelo pode chamar uma ferramenta, ler o resultado e depois decidir chamar outra ferramenta ou responder.

## Como a Chamada de Ferramentas Funciona

1. Você envia uma mensagem
2. O modelo decide que precisa de informação ou ação externa
3. Ele emite uma **chamada de ferramenta** — uma requisição estruturada com nome da ferramenta e argumentos
4. O 橘子岛 executa a ferramenta no dispositivo ou em um servidor remoto
5. O resultado é enviado de volta ao modelo
6. O modelo pode chamar outra ferramenta ou produzir uma resposta final

Este loop pode se repetir várias vezes dentro de um único turno de mensagem.

## Ferramentas Disponíveis

### Busca na Web

Pesquise na internet e busque páginas da web. O modelo pode consultar informações atuais, verificar fatos ou recuperar documentação.

- **Provedores**: DuckDuckGo Lite (padrão, sem chave), Brave, Serper, Tavily, SearXNG
- **Configuração**: Configurações → Busca na Web
- **Guia**: [Busca na Web](web-search.md)

### Geração de Imagens

Gere imagens a partir de prompts de texto usando um modelo dedicado de texto para imagem. As imagens são renderizadas inline na conversa e podem ser visualizadas em tela cheia.

- **Provedor**: BYOK — usa sua própria chave de API e URL base, independente do modelo de chat
- **Configuração**: Configurações → Geração de Imagens
- **Guia**: [Geração de Imagens](image-generation.md)

### Execução de Código

Execute código em um ambiente isolado:

- **Execução de Código do Gemini** — execução de código integrada para modelos Gemini (sem configuração)
- **Sandbox** — ambiente Alpine Linux local via PRoot, com gerenciamento de pacotes e acesso a arquivos SAF

### Shell Remoto

Execute comandos em máquinas remotas via o protocolo [Conch](https://github.com/orangeisland/conch). O modelo pode verificar status do servidor, gerenciar arquivos ou executar scripts.

- **Protocolo**: Criptografado de ponta a ponta (ECDH + AES-256-GCM)
- **Configuração**: Configurações → Shell
- **Guia**: [Shell Remoto](shell.md)

### Operações de Arquivo

Leia, escreva, edite, busque com glob e grep em arquivos em dispositivos remotos através do protocolo Conch. O modelo pode manipular diretamente sistemas de arquivos remotos.

!!! note
    Operações de arquivo requerem um dispositivo shell Conch configurado. Consulte [Shell Remoto](shell.md) para configuração.

### Memória

Armazenamento de conhecimento persistente que abrange conversas:

- **Memória Ativa** — sempre incluída em cada chamada de API. Use para fatos, preferências ou contexto que o modelo deve sempre lembrar.
- **Memórias Salvas** — uma coleção de arquivos de memória nomeados que o modelo pode buscar, ler, escrever e editar via chamadas de ferramentas.

Consulte [Memória & Cache](memory.md) para detalhes.

### Busca em Conversas

O modelo pode buscar no seu histórico de conversas passadas usando métodos de palavra-chave ou semânticos (RAG). Isso permite que ele faça referência a discussões anteriores sem que você precise encontrá-las e compartilhá-las manualmente.

Consulte [Busca em Conversas](search.md) para configuração.

---

## Interface de Ferramentas no Chat

Quando uma ferramenta é chamada, você a verá inline na conversa:

<div class="grid cards" markdown>

- **:material-progress-wrench: Banner de Chamada de Ferramenta**

    ---

    Mostra o nome da ferramenta e um status breve (ex.: :material-magnify: "Buscando 'últimas notícias de IA' na web").

- **:material-check-circle: Resultado da Ferramenta**

    ---

    Após a execução, mostra o resultado formatado ou resumo (ex.: "Encontrados 5 resultados para 'últimas notícias de IA'").

</div>

### Detalhes Expansíveis

Toque em uma chamada de ferramenta para expandi-la e ver:

- **Argumentos** — os parâmetros exatos enviados à ferramenta
- **Resultado** — a saída bruta da execução da ferramenta
- **Status** — sucesso, falha ou resultados parciais

### Chamadas com Falha

Se uma chamada de ferramenta falhar, o modelo é notificado do erro e pode tentar novamente ou ajustar. Você verá um banner vermelho com a mensagem de erro.

---

## Permissões de Ferramentas

Você controla quais ferramentas o modelo pode acessar:

| Configuração | Local | Padrão |
|--------------|-------|--------|
| Busca na Web | Configurações → Busca na Web | Desativado |
| Shell | Configurações → Shell | Desativado |
| Memória (Salvas) | Configurações → Memória → Acessar Memórias Salvas | Desativado |
| Memória (Ativa) | Configurações → Memória → Acessar Memória Ativa | Desativado |
| Conversas Passadas | Configurações → Memória → Acessar Conversas Passadas | Desativado |
| Busca em Conversas | Configurações → Busca em Conversas | Ativado* |

*A capacidade do modelo de buscar conversas depende de ter um modelo de embedding configurado. Sem um, apenas a busca por palavra-chave está disponível.

---

## Loops de Ferramentas em Múltiplas Rodadas

O modelo pode encadear múltiplas chamadas de ferramentas. Por exemplo:

1. Usuário: "Qual é a versão mais recente do kernel Linux e meu servidor está rodando ela?"
2. Modelo chama `web_search("versão mais recente do kernel Linux")`
3. Modelo chama `shell_execute("uname -r", device="meu-servidor")`
4. Modelo compara os resultados e responde

Cada chamada de ferramenta e seu resultado aparecem como itens inline separados na conversa antes da resposta de texto final.

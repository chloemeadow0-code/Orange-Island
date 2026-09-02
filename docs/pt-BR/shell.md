# Shell Remoto (Conch)

O 橘子岛 pode executar comandos em máquinas remotas através do protocolo [Conch](https://github.com/newo-ether/conch) — um shell seguro criptografado de ponta a ponta projetado para agentes de IA.

## Como Funciona

```text
橘子岛 (Android)  ──ECDH + AES-256-GCM──▶  Servidor Conch (Linux/macOS/Windows)
                                           │
                                           ├── Executar comandos
                                           ├── Ler/escrever/editar arquivos
                                           ├── Busca com glob e grep
                                           └── Retornar resultados
```

O modelo decide quando usar o shell — ele pode verificar status do servidor, gerenciar arquivos, executar scripts ou solucionar problemas de forma autônoma.

## Segurança

O Conch usa criptografia forte e proteções anti-abuso:

- **Troca de chaves ECDH** — chaves efêmeras por sessão
- **Criptografia AES-256-GCM** — todo o tráfego criptografado
- **Assinatura HMAC-SHA256** — integridade da mensagem verificada
- **Limitação de taxa por token bucket** — previne abuso
- **Anti-replay baseado em nonce** — cada requisição é única

!!! note
    Os comandos são executados com as permissões do usuário que está rodando o servidor Conch. Use uma conta de usuário restrita para ambientes sensíveis.

---

## Configuração

### Passo 1: Implantar o Servidor Conch

Implante o servidor Conch na sua máquina de destino. Consulte o [repositório Conch](https://github.com/newo-ether/conch) para instruções de configuração.

### Passo 2: Adicionar Dispositivo no 橘子岛

1. Vá em **Configurações → Shell**
2. Ative **Ferramenta Shell**
3. Toque em **Adicionar Dispositivo**
4. Escolha o tipo de dispositivo: **Conch** ou **SSH**
5. Preencha os detalhes do dispositivo:

=== "Conch"

    | Campo | Descrição | Exemplo |
    |-------|-----------|---------|
    | **Nome** | Nome de exibição para este dispositivo | `Servidor de Build` |
    | **Descrição** | Nota opcional sobre esta máquina | `Caixa Ubuntu do escritório` |
    | **URL do Servidor** | Endpoint do servidor Conch (host:porta) | `http://192.168.1.100:14216` |
    | **Chave de API** | Token de autenticação | Da configuração do servidor Conch |
    | **Timeout** | Tempo limite do comando em segundos | `30` |

=== "SSH"

    | Campo | Descrição | Exemplo |
    |-------|-----------|---------|
    | **Nome** | Nome de exibição para este dispositivo | `Servidor VPS` |
    | **Descrição** | Nota opcional sobre esta máquina | `Servidor web de produção` |
    | **Host** | Nome do host SSH ou endereço IP | `192.168.1.200` |
    | **Porta** | Porta SSH | `22` |
    | **Usuário** | Nome de usuário SSH | `root` |
    | **Senha** | Senha SSH | Sua senha SSH |

Toque em **Adicionar** para salvar.

### Passo 3: Usar

Uma vez configurado, o modelo pode acessar o dispositivo. Não há gatilho manual — o modelo descobre automaticamente os dispositivos shell disponíveis e os chama quando apropriado.

---

## Suporte a Múltiplos Dispositivos

Adicione múltiplos dispositivos shell para permitir que o modelo trabalhe em várias máquinas:

- **Servidor de build** — compilar e testar código
- **Home lab** — gerenciar serviços auto-hospedados
- **VM de desenvolvimento** — editar código e executar scripts

Cada dispositivo é configurado independentemente com seu próprio nome, URL e credenciais. O modelo pode distinguir entre eles e escolher o dispositivo certo para cada tarefa.

---

## Operações Disponíveis

### Execução de Comando (`shell_execute`)

Execute qualquer comando shell e receba stdout, stderr e código de saída.

### Operações de Arquivo

| Ferramenta | Função |
|------------|--------|
| `file_read` | Ler um arquivo do sistema de arquivos remoto |
| `file_write` | Escrever ou sobrescrever um arquivo |
| `file_edit` | Realizar substituições exatas de string em um arquivo |
| `file_glob` | Encontrar arquivos correspondentes a um padrão glob |
| `file_grep` | Buscar conteúdo de arquivos com regex |

Todas as operações de arquivo passam pelo canal criptografado Conch.

---

## Integração MCP

O Conch também pode servir como um **servidor MCP para Claude Desktop**. Se você usa o Claude Code ou outro cliente MCP, pode configurar o Conch como um provedor de ferramentas para acesso remoto a arquivos e shell a partir do seu desktop.

Consulte a [documentação do Conch](https://github.com/newo-ether/conch) para instruções de configuração MCP.

---

## Solução de Problemas

### Dispositivo aparece como indisponível
- Verifique se o servidor Conch está rodando
- Verifique se a URL é alcançável a partir do seu dispositivo Android
- Verifique as regras de firewall no servidor

### Comandos expiram
- Aumente o timeout nas configurações do dispositivo
- Verifique se o comando não está travado (exigindo entrada do usuário, etc.)

### Falha na autenticação
- Verifique se a chave de API corresponde à configuração do servidor
- Regenere as chaves se necessário

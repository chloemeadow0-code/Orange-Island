# Proxy de Rede

Roteia todo o tráfego de rede do 橘子岛 através de um proxy HTTP ou SOCKS. Útil em redes restritas, para rotear solicitações através de um gateway específico, ou quando um provedor só é acessível via proxy.

O proxy se aplica a **todo** o tráfego de saída: provedores de chat, busca de modelos, pesquisa na web, embeddings, busca de páginas web e envio de relatórios de falhas.

## Configuração

Abra **Configurações → Rede → Proxy** e ative **Habilitar proxy**, então configure:

| Campo | Descrição |
|-------|-----------|
| **Tipo** | `HTTP`, `HTTPS` ou `SOCKS5`. HTTP/HTTPS tunelam o tráfego HTTPS via `CONNECT`; SOCKS5 encaminha no nível de socket. |
| **Host** | Nome do host ou IP do servidor proxy (ex. `127.0.0.1`). |
| **Porta** | Porta do servidor proxy (ex. `7890`). |
| **Usuário / Senha** | Opcional. Necessário apenas se seu proxy exigir autenticação. |

As alterações entram em vigor imediatamente — não é necessário reiniciar o aplicativo.

## Lista de bypass

Hosts e intervalos de endereços na **lista de bypass** conectam-se **diretamente**, ignorando o proxy. Coloque uma entrada por linha. A lista padrão mantém endereços de loopback e privados (LAN) diretos:

```
localhost
127.0.0.1
10.0.0.0/8
172.16.0.0/12
192.168.0.0/16
::1
```

Cada linha pode ser:

- um host exato — `localhost`, `192.168.1.10`
- um intervalo IPv4 CIDR — `10.0.0.0/8`
- um sufixo curinga — `*.example.com`

É por isso que um servidor Ollama local (ex. `http://192.168.1.50:11434`) continua funcionando na sua LAN enquanto todo o resto passa pelo proxy.

## Observações

- O tipo **HTTPS** usa o mesmo protocolo de proxy que HTTP (um proxy HTTP `CONNECT`); escolha-o se seu proxy estiver rotulado como "HTTPS".
- A senha do proxy é incluída nas **exportações de dados criptografados apenas quando "Incluir chaves de API" está habilitado**.
- Se as solicitações falharem com tempos limite após habilitar o proxy, verifique o host/porta e se o tipo de proxy corresponde ao seu servidor.

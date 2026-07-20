# Sandbox

O 橘子岛 pode executar um ambiente Alpine Linux leve localmente no seu dispositivo — sem necessidade de conexão com a internet. O sandbox permite que o modelo instale pacotes e execute comandos em um sistema de arquivos raiz isolado.

!!! note "Disponibilidade"
    O sandbox está disponível em todas as builds. Acesse-o em **Configurações → Shell → Gerenciamento do Sandbox** ou diretamente via **Configurações → Sandbox**.

## Como Funciona

O sandbox usa um sistema de arquivos raiz Alpine Linux implantado no armazenamento privado do aplicativo no seu dispositivo. Um gerenciador de pacotes mínimo baseado em `apk` permite instalar software neste ambiente, e os comandos são executados dentro de um contêiner baseado em proot.

Isso **não** é uma máquina virtual completa — é um contêiner leve em espaço de usuário que compartilha o kernel do host. Ele fornece isolamento suficiente para experimentação segura, mantendo o uso de recursos baixo.

---

## Interferência de VPN — Crítico

!!! danger "Desligue Sua VPN Antes de Usar a Rede do Sandbox"

    Aplicativos de VPN interferem na resolução DNS do proot. Aqui está o porquê:

    **Causa raiz — PRoot não tem isolamento de namespace de rede.**

    O PRoot usa `ptrace` para interceptar chamadas de sistema e redirecionar caminhos de arquivo, mas ele **não** suporta `CLONE_NEWNET` (namespaces de rede do Linux). Todos os processos dentro do sandbox compartilham a pilha de rede do sistema Android host diretamente. Não há interface de rede virtual, nem tabela de roteamento isolada, nem configuração DNS independente.

    **Como uma VPN no Android quebra o DNS dentro do proot:**

    1. Aplicativos de VPN no Android usam a API `VpnService`, que cria uma **interface TUN** — um dispositivo de rede virtual que intercepta **todo** o tráfego do dispositivo, incluindo o tráfego de dentro do proot
    2. Para evitar vazamentos de DNS fora do túnel criptografado, a VPN **redireciona todo o tráfego da porta 53 (DNS)** para seus próprios servidores DNS
    3. Dentro do proot, quando uma aplicação chama `getaddrinfo()` (o resolvedor DNS padrão da libc), a requisição passa pelo resolvedor do sistema Android — que a VPN já interceptou
    4. No Android 12+, o Google reformulou o resolvedor DNS, tornando `getaddrinfo()` dentro de ambientes proot particularmente frágil ([termux/proot#215](https://github.com/termux/proot/issues/215))
    5. O roteamento TUN da VPN e o caminho DNS do resolvedor do sistema entram em conflito dentro do proot: o resolvedor envia uma consulta DNS, a TUN da VPN a intercepta, mas a resposta nunca chega de volta através da camada `ptrace` do proot

    **Sintomas observados:**

    | Operação | Resultado |
    |----------|-----------|
    | `ping 1.1.1.1` | ✅ Funciona (IP direto, sem necessidade de DNS) |
    | `ping google.com` | ❌ Falha — "Falha temporária na resolução de nomes" |
    | `apk add python3` | ❌ Falha — não consegue resolver `dl-cdn.alpinelinux.org` |
    | `curl https://example.com` | ❌ Falha — erro de resolução de nomes |
    | `curl https://1.1.1.1` | ✅ Funciona (conexão direta por IP) |

    **Solução:** Desligue completamente sua VPN antes de realizar qualquer operação de rede no sandbox (instalar pacotes, `curl`, `wget`, etc.). Você pode reativar a VPN após a conclusão das operações de rede.

    Esta é uma limitação fundamental da arquitetura do proot — ele não pode virtualizar a pilha de rede quando uma VPN do Android substitui o roteamento DNS do sistema via uma interface TUN.

---

## Configuração

### Instalar o Sistema de Arquivos Raiz

Na primeira vez que você abrir o sandbox, verá um painel indicando que o rootfs não está instalado. Toque em **Instalar** para baixar e extrair o sistema de arquivos raiz Alpine.

!!! info "Uso de Armazenamento"
    O rootfs base usa aproximadamente 100–200 MB. Os pacotes instalados consomem espaço adicional. O uso total de disco é mostrado no painel.

---

## Gerenciamento de Pacotes

### Instalar um Pacote

1. Digite o nome do pacote no campo de texto (ex.: `python3`)
2. Toque em **Instalar**
3. Observe a saída do terminal para o progresso da instalação

Alternativamente, toque em qualquer **chip de instalação rápida** para pacotes comuns:

```
python3   git      curl      wget
openssh   nodejs   build-base   htop
```

### Pacotes Instalados

Abaixo da seção de instalação, todos os pacotes instalados são listados com:

- **Nome** — o nome do pacote Alpine
- **Versão** — a versão instalada
- **Descrição** — um breve resumo (truncado)

### Remover um Pacote

Toque no ícone :material-close: em qualquer pacote instalado para removê-lo. Um diálogo de confirmação aparece antes da exclusão.

---

## Painel

Quando o sandbox está pronto, o painel mostra:

- **Uso de disco** — uma barra de progresso e exibição numérica (MB ou GB)
- **Contagem de instalados** — número total de pacotes

---

## Saída do Terminal

Ao instalar ou remover pacotes, a saída do terminal aparece em uma visão monoespaçada rolável com tema escuro abaixo do campo de entrada. A saída rola automaticamente para acompanhar as últimas linhas.

Use isso para:
- Monitorar o progresso da instalação
- Depurar operações de pacote com falha
- Ver quais arquivos um pacote instala

---

## Redefinir Sandbox

A **Zona de Perigo** na parte inferior contém uma opção **Redefinir Sandbox**. Isso remove completamente o sistema de arquivos raiz e todos os pacotes instalados.

!!! danger "Ação Destrutiva"
    Redefinir o sandbox exclui todo o ambiente Alpine. Você precisará reinstalar o rootfs e todos os pacotes posteriormente. Um diálogo de confirmação previne redefinições acidentais.

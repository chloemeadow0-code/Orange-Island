# Geração de Imagens

Gere imagens a partir de prompts de texto usando um modelo de texto para imagem, diretamente nas suas conversas.

## O Que Faz

Quando a geração de imagens está ativada, o 橘子岛 pode transformar seus prompts em imagens usando um modelo dedicado de texto para imagem (como DALL·E, GPT-Image, Imagen, FLUX, Stable Diffusion, Seedream, Qwen-Image e muitos outros). A imagem gerada é retornada na conversa, para que você possa iterar sobre ela como qualquer outra resposta.

A geração de imagens usa sua **própria seleção de modelo**, independente do modelo com o qual você conversa — então você pode conversar com um modelo e gerar imagens com outro.

## Configuração

1. Vá em **Configurações → Geração de Imagens**
2. Alterne **Ativar Geração de Imagens** para ligado
3. Toque em **Modelo** e escolha um modelo de texto para imagem
4. Opcionalmente, defina o **Tamanho Padrão** (largura × altura)

!!! note "BYOK — credenciais dedicadas"
    A geração de imagens usa sua **própria chave de API e URL base dedicadas**, independentes dos seus provedores de chat. Isso significa que você pode usar um serviço diferente para imagens do que para chat. Vá em **Configurações → Geração de Imagens** para configurar a chave, URL base, modelo e tamanho padrão.

## Seleção de Modelo

Toque em **Modelo** para escolher o modelo usado para geração.

- O seletor mostra modelos que parecem ser de texto para imagem, filtrados de todos os seus modelos sincronizados, para que a lista fique curta.
- Se o modelo que você quer não estiver listado (nome incomum), ative **Mostrar todos os modelos** para escolher da lista completa.
- Apenas uma entrada `Provedor:modelo` devidamente sincronizada conta como uma seleção válida. Sincronize seus modelos primeiro em **Configurações → Provedores de API** / **Gerenciar Modelos** se a lista estiver vazia.

## Tamanho Padrão

Define as dimensões de saída padrão, inseridas como **largura × altura** em pixels (por exemplo, `1024` × `1024`).

- O padrão é `1024 × 1024`.
- Os tamanhos suportados dependem do modelo e do provedor — se um modelo rejeitar um tamanho, tente um valor documentado (opções comuns são `1024×1024`, `1024×1792`, `1792×1024`).

## Como Funciona

1. Ative a geração de imagens e selecione um modelo de imagem
2. Em uma conversa, peça ao assistente para criar uma imagem
3. O 橘子岛 encaminha a requisição para o modelo de imagem configurado usando as credenciais daquele provedor
4. A imagem gerada é retornada na conversa

!!! tip
    Seja específico no seu prompt — descreva o assunto, estilo, composição e iluminação. Prompts claros produzem resultados muito melhores do que os vagos.

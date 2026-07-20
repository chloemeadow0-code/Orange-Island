# Génération d'Images

Générez des images à partir de prompts textuels en utilisant un modèle texte-image, directement dans vos conversations.

## Ce Que Cela Fait

Lorsque la génération d'images est activée, 橘子岛 peut transformer vos prompts en images en utilisant un modèle texte-image dédié (tel que DALL·E, GPT-Image, Imagen, FLUX, Stable Diffusion, Seedream, Qwen-Image et bien d'autres). L'image générée est retournée dans la conversation, vous permettant d'itérer dessus comme n'importe quelle autre réponse.

La génération d'images utilise sa **propre sélection de modèle**, indépendante du modèle avec lequel vous discutez — vous pouvez donc discuter avec un modèle et générer des images avec un autre.

## Configuration

1. Allez dans **Paramètres → Génération d'Images**
2. Activez **Activer la Génération d'Images**
3. Appuyez sur **Modèle** et choisissez un modèle texte-image
4. Définissez éventuellement la **Taille par Défaut** (largeur × hauteur)

!!! note "BYOK — identifiants dédiés"
    La génération d'images utilise sa **propre clé API et URL de base dédiées**, indépendantes de vos fournisseurs de chat. Cela signifie que vous pouvez utiliser un service différent pour les images que pour le chat. Allez dans **Paramètres → Génération d'Images** pour configurer la clé, l'URL de base, le modèle et la taille par défaut.

## Sélection de Modèle

Appuyez sur **Modèle** pour choisir le modèle utilisé pour la génération.

- Le sélecteur affiche les modèles qui ressemblent à des modèles texte-image, filtrés parmi tous vos modèles synchronisés, pour que la liste reste courte.
- Si le modèle que vous voulez n'est pas listé (un nom inhabituel), activez **Afficher tous les modèles** pour choisir dans la liste complète.
- Seule une entrée `Fournisseur:modèle` correctement synchronisée compte comme une sélection valide. Synchronisez d'abord vos modèles sous **Paramètres → Fournisseurs API** / **Gérer les Modèles** si la liste est vide.

## Taille par Défaut

Définit les dimensions de sortie par défaut, saisies en **largeur × hauteur** en pixels (par exemple `1024` × `1024`).

- La valeur par défaut est `1024 × 1024`.
- Les tailles prises en charge dépendent du modèle et du fournisseur — si un modèle rejette une taille, essayez une valeur qu'il documente (les options courantes sont `1024×1024`, `1024×1792`, `1792×1024`).

## Comment Ça Marche

1. Activez la génération d'images et sélectionnez un modèle d'image
2. Dans une conversation, demandez à l'assistant de créer une image
3. 橘子岛 achemine la requête vers le modèle d'image configuré en utilisant les identifiants de ce fournisseur
4. L'image générée est retournée dans la conversation

!!! tip
    Soyez précis dans votre prompt — décrivez le sujet, le style, la composition et l'éclairage. Des prompts clairs produisent de bien meilleurs résultats que des prompts vagues.

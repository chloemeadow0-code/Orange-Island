# Modèles

Gérez les modèles d'IA disponibles et définissez votre modèle par défaut pour les conversations.

## Liste des Modèles

La page **Modèles** affiche tous les modèles connus d'橘子岛, organisés par fournisseur :

- **Modèle par Défaut** — Le modèle utilisé pour les nouvelles conversations. Appuyez pour changer.
- **Modèles Disponibles** — Développez chaque fournisseur pour voir ses modèles. Activez ceux que vous souhaitez utiliser.

### Activer / Désactiver les Modèles

Cochez ou décochez la case à côté d'un modèle pour basculer sa disponibilité. Les modèles désactivés n'apparaîtront pas dans le sélecteur de modèle dans les conversations.

### Renommer les Modèles

Appuyez sur l'icône d'édition (stylo) à côté d'un modèle pour lui donner un alias personnalisé. Cet alias apparaît dans toute l'application au lieu de l'ID technique du modèle.

### Synchroniser les Modèles

Appuyez sur **Synchroniser les Modèles** pour récupérer les derniers modèles disponibles auprès de tous les fournisseurs API configurés. Cela nécessite une connexion internet et des clés API valides.

!!! tip "Modèles Locaux"
    Les modèles locaux apparaissent sous la section du fournisseur **Local**. Ils sont gérés séparément dans **Paramètres → Fournisseurs → Local**.

---

## Modèle par Défaut

Le **Modèle par Défaut** est utilisé pour toutes les nouvelles conversations. Pour le changer :

1. Appuyez sur la ligne du modèle par défaut en haut de la page Modèles
2. Sélectionnez un modèle dans la liste (seuls les modèles activés sont affichés)
3. Le changement prend effet immédiatement

Vous pouvez remplacer le modèle par conversation depuis le sélecteur de modèle de l'écran de chat.

---

## Alias de Modèles

Les alias de modèles vous permettent de donner des noms conviviaux aux modèles avec de longs identifiants techniques. Par exemple, vous pourriez renommer `openai/gpt-4o-mini` en simplement "GPT-4o Mini".

Les alias sont affichés partout : le sélecteur de modèle, les en-têtes de conversation et les pages de paramètres.

Pour supprimer un alias, videz le champ de texte et enregistrez.

---

## Dépannage

### Les modèles n'apparaissent pas

- Appuyez sur **Synchroniser les Modèles** pour actualiser la liste
- Vérifiez que vous avez une clé API valide pour le fournisseur dans **Paramètres → Fournisseurs**
- Vérifiez votre connexion internet
- Certains fournisseurs peuvent être temporairement indisponibles

### Les modèles locaux ne sont pas affichés

- Importez un fichier de modèle GGUF dans **Paramètres → Fournisseurs → Local**
- Le modèle doit être au format GGUF valide

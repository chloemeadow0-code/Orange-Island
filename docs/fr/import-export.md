# Portabilité des Données

橘子岛 stocke toutes vos données sur l'appareil et fournit des capacités complètes d'import/export. Vous possédez vos données — déplacez-les, exportez-les, sauvegardez-les.

## Export

Exportez vos données vers un seul fichier `.橘子岛` — une archive portable qui contient tout ce qu'橘子岛 stocke.

### Ce Qui Est Exporté

Vous choisissez ce qu'il faut inclure :

| Catégorie | Contenu |
|-----------|---------|
| **Conversations & Messages** | Tout l'historique de chat, les arbres de messages, les branches |
| **Mémoires** | Mémoire active et tous les fichiers de mémoire sauvegardés |
| **Prompts Système** | Tous les modèles de prompts système personnalisés |
| **Paramètres** | Configuration et préférences de l'application |
| **Clés API** | Toutes les clés API configurées |

!!! danger "Avertissement sur les Clés API"
    Les clés API sont exportées en **texte brut**. Toute personne ayant le fichier `.橘子岛` peut lire vos clés. N'activez l'export des clés API que si vous faites confiance à la destination et manipulez le fichier de manière sécurisée.

### Comment Exporter

1. Allez dans **Paramètres → Contrôle des Données**
2. Appuyez sur **Exporter les Données**
3. Sélectionnez les catégories à inclure
4. Appuyez sur **Exporter**
5. Choisissez où enregistrer le fichier `.橘子岛`

---

## Import

Restaurez les données à partir d'un export `.橘子岛` précédent.

### Stratégies d'Import

Lors de l'import, vous choisissez comment 橘子岛 gère les données qui existent déjà sur votre appareil :

| Stratégie | Comportement |
|-----------|--------------|
| **Fusion** | Ajouter les nouveaux éléments, conserver les existants. Si un élément avec le même ID existe, la version importée l'écrase. |
| **Remplacement** | Effacer toutes les données existantes dans les catégories sélectionnées, puis importer. Un nouveau départ. |
| **Ignorer** | Importer uniquement les éléments sans conflit. Les éléments existants ne sont pas touchés. |

!!! tip
    Utilisez **Fusion** dans la plupart des cas — cela ajoute en toute sécurité de nouvelles données tout en préservant ce qui est déjà sur votre appareil.

### Comment Importer

1. Allez dans **Paramètres → Contrôle des Données**
2. Appuyez sur **Importer des Données**
3. Sélectionnez un fichier `.橘子岛`
4. Examinez l'aperçu d'import — voyez ce qui est dans le fichier (date d'export, version, nombre d'éléments)
5. Choisissez une stratégie d'import
6. Appuyez sur **Importer**

!!! danger "Avertissement sur les Clés API"
    Si le fichier d'export contient des clés API, 橘子岛 vous avertit avant l'import. Les clés sont importées en texte brut. Ne procédez que si vous faites confiance à la source du fichier.

---

## Import Tiers

Importez des conversations depuis d'autres plateformes de chat IA.

Claude et ChatGPT exportent tous deux vos données sous forme d'**archive `.zip`**. 橘子岛 importe ce `.zip` directement — il n'est pas nécessaire de le décompresser d'abord, et 橘子岛 n'accepte **pas** les fichiers `.json` isolés.

### Importer depuis Claude

**1. Exporter depuis Claude.** Allez sur [Claude](https://claude.ai/) → **Paramètres → Contrôle des Données → Exporter les données**. Claude prépare l'archive rapidement — généralement en **moins d'une heure** — et vous envoie un lien de téléchargement par e-mail.

!!! warning "Téléchargez rapidement"
    Le lien de téléchargement de Claude **expire rapidement**. Récupérez le `.zip` dès que l'e-mail arrive — si vous attendez trop, le lien devient mort et vous devrez demander un nouvel export.

**2. Importer dans 橘子岛.**

1. Allez dans **Paramètres → Contrôle des Données → Tiers → Importer depuis Claude**
2. Sélectionnez le fichier `.zip` exporté
3. Examinez l'aperçu — voyez le nombre de conversations et de messages
4. Choisissez la stratégie **Fusion** ou **Remplacement**
5. Appuyez sur **Importer**

!!! note
    橘子岛 lit les données de conversation directement depuis l'export `.zip` de Claude. Les pièces jointes sont détectées et affichées dans l'aperçu, mais seul le texte des messages est importé — les fichiers de pièces jointes eux-mêmes ne le sont pas.

### Importer depuis ChatGPT

**1. Exporter depuis ChatGPT.** Allez sur [ChatGPT](https://chatgpt.com/) → **Paramètres → Contrôle des Données → Exporter les données**. ChatGPT traite la demande et vous envoie un lien de téléchargement par e-mail quand c'est prêt.

!!! info "Soyez patient"
    L'export de ChatGPT prend généralement **1 à 2 jours** pour arriver. C'est normal — attendez l'e-mail plutôt que de redemander.

**2. Importer dans 橘子岛.**

1. Allez dans **Paramètres → Contrôle des Données → Tiers → Importer depuis ChatGPT**
2. Sélectionnez le fichier `.zip` téléchargé
3. Examinez l'aperçu
4. Choisissez la stratégie **Fusion** ou **Remplacement**
5. Appuyez sur **Importer**

!!! note
    Les messages utilisateur et assistant sont importés. Les rôles des messages sont préservés.

---

## Format de Fichier

Le fichier `.橘子岛` est une archive basée sur JSON. Si vous êtes techniquement incliné, vous pouvez l'inspecter ou le traiter avec des outils standards. Le format est conçu pour la compatibilité ascendante et descendante.

---

## Sauvegarde Automatique

橘子岛 peut sauvegarder automatiquement vos données selon un planning. Vous n'avez pas besoin de penser à exporter — 橘子岛 s'en charge pour vous.

### Comment Ça Marche

- La sauvegarde automatique s'exécute périodiquement en arrière-plan en utilisant Android WorkManager
- Lorsqu'une sauvegarde est due, 橘子岛 exporte vos catégories sélectionnées vers le répertoire configuré
- Une notification apparaît uniquement si une sauvegarde échoue — les sauvegardes réussies sont silencieuses
- Les anciennes sauvegardes sont automatiquement supprimées selon vos paramètres de rétention

### Configuration

1. Allez dans **Paramètres → Contrôle des Données → Sauvegarde Automatique**
2. Activez/Désactivez **Sauvegarde Automatique**
3. Définissez **Sauvegarder tous les** — choisissez 1 jour, 3 jours, 5 jours, 1 semaine ou 1 mois
4. Choisissez **Contenu de l'export** — sélectionnez les catégories à inclure. Les clés API **peuvent** être incluses (un avertissement s'affiche lorsque vous cochez cette case) — ne l'activez que si l'emplacement de sauvegarde est privé et sécurisé. Les clés API ne sont **pas** incluses par défaut.
5. Définissez **Emplacement de sauvegarde** — appuyez pour choisir un dossier (par défaut `Download/橘子岛/Backup`)
6. Activez/Désactivez **Supprimer automatiquement les anciennes sauvegardes** et définissez la période **Supprimer les sauvegardes plus anciennes que**

!!! info "Contrainte de Suppression Automatique"
    La période de suppression doit être plus longue que la période de sauvegarde. Par exemple, si vous sauvegardez chaque semaine, les sauvegardes peuvent être supprimées automatiquement après 1 mois ou 1 an — jamais plus tôt. Cela empêche de supprimer votre seule sauvegarde avant qu'une nouvelle ne soit créée.

!!! note
    La sauvegarde automatique utilise Android WorkManager pour garantir la fiabilité même si l'application est fermée ou l'appareil redémarre. Les sauvegardes peuvent être légèrement retardées pendant le mode Doze pour économiser la batterie.

---

## Bonnes Pratiques

- **Exportez régulièrement** comme sauvegarde — conservez le fichier dans un endroit sûr
- **Activez la Sauvegarde Automatique** pour une protection planifiée sans intervention
- **N'incluez pas les clés API** dans les exports de routine — activez l'export des clés uniquement pour les migrations complètes d'appareil
- **Utilisez Fusion pour les imports incrémentiels** — Remplacement est destructif
- **Prévisualisez avant d'importer** — vérifiez la date d'export et le nombre d'éléments pour confirmer que c'est le bon fichier

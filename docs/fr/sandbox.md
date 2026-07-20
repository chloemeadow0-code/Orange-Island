# Sandbox

橘子岛 peut exécuter un environnement Alpine Linux léger localement sur votre appareil — aucune connexion internet requise. La sandbox permet au modèle d'installer des paquets et d'exécuter des commandes dans un système de fichiers racine isolé.

!!! note "Disponibilité"
    La sandbox est disponible dans toutes les builds. Accédez-y depuis **Paramètres → Shell → Gestion de la Sandbox** ou directement via **Paramètres → Sandbox**.

## Comment Ça Marche

La sandbox utilise un système de fichiers racine Alpine Linux déployé dans le stockage privé de l'application. Un gestionnaire de paquets minimal basé sur `apk` vous permet d'installer des logiciels dans cet environnement, et les commandes s'exécutent dans un conteneur basé sur proot.

Ce n'est **pas** une machine virtuelle complète — c'est un conteneur léger en espace utilisateur qui partage le noyau hôte. Il fournit suffisamment d'isolation pour une expérimentation sûre tout en maintenant une faible utilisation des ressources.

---

## Interférence VPN — Critique

!!! danger "Désactivez Votre VPN Avant d'Utiliser le Réseau de la Sandbox"

    Les applications VPN interfèrent avec la résolution DNS de proot. Voici pourquoi :

    **Cause racine — PRoot n'a pas d'isolation d'espace de noms réseau.**

    PRoot utilise `ptrace` pour intercepter les appels système et rediriger les chemins de fichiers, mais il ne prend **pas** en charge `CLONE_NEWNET` (espaces de noms réseau Linux). Tous les processus à l'intérieur de la sandbox partagent directement la pile réseau du système Android hôte. Il n'y a pas d'interface réseau virtuelle, pas de table de routage isolée et pas de configuration DNS indépendante.

    **Comment un VPN sur Android casse le DNS à l'intérieur de proot :**

    1. Les applications VPN Android utilisent l'API `VpnService`, qui crée une **interface TUN** — un périphérique réseau virtuel qui intercepte **tout** le trafic de l'appareil, y compris le trafic à l'intérieur de proot
    2. Pour éviter les fuites DNS hors du tunnel chiffré, le VPN **redirige tout le trafic du port 53 (DNS)** vers ses propres serveurs DNS
    3. À l'intérieur de proot, lorsqu'une application appelle `getaddrinfo()` (le résolveur DNS standard de libc), la requête passe par le résolveur système Android — que le VPN a déjà intercepté
    4. Sur Android 12+, Google a retravaillé le résolveur DNS, rendant `getaddrinfo()` à l'intérieur des environnements proot particulièrement fragile ([termux/proot#215](https://github.com/termux/proot/issues/215))
    5. Le routage TUN du VPN et le chemin DNS du résolveur système entrent en conflit à l'intérieur de proot : le résolveur envoie une requête DNS, le TUN du VPN l'intercepte, mais la réponse ne remonte jamais à travers la couche `ptrace` de proot

    **Symptômes observés :**

    | Opération | Résultat |
    |-----------|----------|
    | `ping 1.1.1.1` | ✅ Fonctionne (IP directe, pas de DNS nécessaire) |
    | `ping google.com` | ❌ Échoue — "Échec temporaire de résolution de nom" |
    | `apk add python3` | ❌ Échoue — impossible de résoudre `dl-cdn.alpinelinux.org` |
    | `curl https://example.com` | ❌ Échoue — erreur de résolution de nom |
    | `curl https://1.1.1.1` | ✅ Fonctionne (connexion IP directe) |

    **Solution :** Désactivez complètement votre VPN avant d'effectuer toute opération réseau dans la sandbox (installation de paquets, `curl`, `wget`, etc.). Vous pouvez réactiver le VPN une fois les opérations réseau terminées.

    C'est une limitation fondamentale de l'architecture de proot — il ne peut pas virtualiser la pile réseau lorsqu'un VPN Android remplace le routage DNS du système via une interface TUN.

---

## Configuration

### Installer le Système de Fichiers Racine

La première fois que vous ouvrez la sandbox, vous verrez un tableau de bord indiquant que le rootfs n'est pas installé. Appuyez sur **Installer** pour télécharger et extraire le système de fichiers racine Alpine.

!!! info "Utilisation du Stockage"
    Le rootfs de base utilise environ 100–200 Mo. Les paquets installés consomment de l'espace supplémentaire. L'utilisation totale du disque est affichée sur le tableau de bord.

---

## Gestion des Paquets

### Installer un Paquet

1. Tapez le nom du paquet dans le champ de texte (par ex., `python3`)
2. Appuyez sur **Installer**
3. Observez la sortie du terminal pour la progression de l'installation

Alternativement, appuyez sur n'importe quelle **pastille d'installation rapide** pour les paquets courants :

```
python3   git      curl      wget
openssh   nodejs   build-base   htop
```

### Paquets Installés

Sous la section d'installation, tous les paquets installés sont listés avec :

- **Nom** — le nom du paquet Alpine
- **Version** — la version installée
- **Description** — un bref résumé (tronqué)

### Supprimer un Paquet

Appuyez sur l'icône :material-close: sur n'importe quel paquet installé pour le supprimer. Une boîte de dialogue de confirmation apparaît avant la suppression.

---

## Tableau de Bord

Lorsque la sandbox est prête, le tableau de bord affiche :

- **Utilisation du disque** — une barre de progression et un affichage numérique (Mo ou Go)
- **Nombre installé** — nombre total de paquets

---

## Sortie du Terminal

Lors de l'installation ou de la suppression de paquets, la sortie du terminal apparaît dans une vue monospace sombre et défilable sous le champ de saisie. La sortie défile automatiquement pour suivre les dernières lignes.

Utilisez-la pour :
- Surveiller la progression de l'installation
- Déboguer les opérations de paquets échouées
- Voir quels fichiers un paquet installe

---

## Réinitialiser la Sandbox

La **Zone de Danger** en bas contient une option **Réinitialiser la Sandbox**. Cela supprime complètement le système de fichiers racine et tous les paquets installés.

!!! danger "Action Destructive"
    Réinitialiser la sandbox supprime tout l'environnement Alpine. Vous devrez réinstaller le rootfs et tous les paquets par la suite. Une boîte de dialogue de confirmation empêche les réinitialisations accidentelles.

const fs = require('fs');
const file = 'app/src/main/java/com/example/ui/screens/ChatScreens.kt';
const lines = fs.readFileSync(file, 'utf8').split('\n');

const membersTabContent = lines.slice(5054, 5250);

const newLines = [
    ...lines.slice(0, 5054),
    ...lines.slice(5254)
];

newLines.push(...membersTabContent);

fs.writeFileSync(file, newLines.join('\n'));

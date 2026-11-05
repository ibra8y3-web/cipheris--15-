const fs = require('fs');
const lines = fs.readFileSync('app/src/main/java/com/example/ui/screens/ChatScreens.kt', 'utf8').split('\n');

// MembersTabContent is from line 5055 (index 5054) to 5250 (index 5249)
const membersTabContent = lines.slice(5054, 5250);

// We need to delete from 5054 to 5254 (index 5253)
const newLines = [
    ...lines.slice(0, 5054),
    ...lines.slice(5254)
];

// Append MembersTabContent to the end
newLines.push(...membersTabContent);

fs.writeFileSync('app/src/main/java/com/example/ui/screens/ChatScreens.kt', newLines.join('\n'));

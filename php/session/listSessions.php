
<?php

if ($handle = opendir('.')) {
    echo "Directory handle: $handle\n";
    echo "Files:\n";

    /* Das ist der korrekte Weg, ein Verzeichnis zu durchlaufen. */
    while (false !== ($file = readdir($handle))) {
        echo "$file\n";
    }

    /* Dies ist der FALSCHE Weg, ein Verzeichnis zu durchlaufen. */
    while ($file = readdir($handle)) {
        echo "$file\n";
    }

    closedir($handle);
}
?>

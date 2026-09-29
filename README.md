# Cella

English | [日本語](README.ja.md)

A chest with more than one page. Seven forms, fed with experience and grown into one
another, from 54 slots up to 221,184.

*Cella* is Latin for a storeroom, and also a compartment inside one.

## Forms

| | slots | large chests | page | pages |
|---|---:|---:|---|---:|
| Larval Cella | 54 | 1 | 6×9 | 1 |
| Imperfect Cella | 216 | 4 | 6×9 | 4 |
| Semi-Perfect Cella | 1,728 | 32 | 6×16 | 18 |
| Cella Jr. | 3,456 | 64 | 12×16 | 18 |
| Perfect Cella | 13,824 | 256 | 12×16 | 72 |
| Super Perfect Cella | 55,296 | 1,024 | 12×16 | 288 |
| Cella Max | 221,184 | 4,096 | 12×16 | 1,152 |

A Cella is fed experience with shift and right click. Two of the seven are not crafted: a
Larval Cella that has been fed enough becomes an Imperfect on its own, and a Perfect Cella
that has been fed enough and is then set off in the End comes back Super Perfect.

The chest keeps itself sorted and remembers what you moved in and out of it. From
Semi-Perfect up it can also be cut into partitions and searched across every page.

## Documents

| | |
|---|---|
| [Forms](docs/forms.md) | Each form: capacity, recipe, what it takes to grow, what it survives |
| [The screen](docs/screen.md) | Pages, partitions, searching, the order it is kept in, the buttons |
| [Config](docs/config.md) | Every setting |

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Java | 21 |

## Building

```
run.bat                   # compile and launch a dev client
gradlew build             # produce the jar
gradlew runGameTestServer # run the game tests
gradlew runData           # regenerate textures, models, recipes and language files
```

`JAVA_HOME` must point at a JDK 21, or `java` must be on `PATH`.

## License

MIT. See [LICENSE](LICENSE).

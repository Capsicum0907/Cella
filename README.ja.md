# Cella

[English](README.md) | 日本語

ページを複数持つチェストです。7つの形があり、経験値を与えて次の形へ育てます。容量は54スロットから221,184スロットまで。

*Cella* はラテン語で貯蔵室、そしてその中の一区画を指します。

## 形

| | スロット | ラージチェスト換算 | 1ページ | ページ数 |
|---|---:|---:|---|---:|
| Larval Cella | 54 | 1 | 6×9 | 1 |
| Imperfect Cella | 216 | 4 | 6×9 | 4 |
| Semi-Perfect Cella | 1,728 | 32 | 6×16 | 18 |
| Cella Jr. | 3,456 | 64 | 12×16 | 18 |
| Perfect Cella | 13,824 | 256 | 12×16 | 72 |
| Super Perfect Cella | 55,296 | 1,024 | 12×16 | 288 |
| Cella Max | 221,184 | 4,096 | 12×16 | 1,152 |

経験値はスニークしながら右クリックで与えます。7つのうち2つはクラフトできません。Larval Cella は十分に与えると自分で Imperfect になり、Perfect Cella は満たしたうえでジ・エンドで自爆させると Super Perfect になって戻ってきます。

中身は常に整列した状態に保たれ、出し入れした内容も記録されます。Semi-Perfect 以降はパーティションに分けることができ、全ページを横断して検索もできます。

## ドキュメント

| | |
|---|---|
| [形](docs/forms.ja.md) | 各形の容量・レシピ・育てるのに要るもの・耐えるもの |
| [画面](docs/screen.ja.md) | ページ・パーティション・検索・並び順・ボタン |
| [設定](docs/config.ja.md) | 全項目 |

## 動作環境

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Java | 21 |

## ビルド

```
run.bat                   # コンパイルして開発用クライアントを起動
gradlew build             # jar を作る
gradlew runGameTestServer # ゲームテストを走らせる
gradlew runData           # テクスチャ・モデル・レシピ・言語ファイルを再生成
```

`JAVA_HOME` が JDK 21 を指しているか、`java` が `PATH` にある必要があります。

## ライセンス

MIT。[LICENSE](LICENSE) を参照してください。

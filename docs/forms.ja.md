# 形

[English](forms.md) | 日本語

[README](../README.ja.md) に戻る。

## 容量

| | スロット | ラージチェスト換算 | 1ページ | ページ数 |
|---|---:|---:|---|---:|
| Larval Cella | 54 | 1 | 6×9 | 1 |
| Imperfect Cella | 216 | 4 | 6×9 | 4 |
| Semi-Perfect Cella | 1,728 | 32 | 6×16 | 18 |
| Cella Jr. | 3,456 | 64 | 12×16 | 18 |
| Perfect Cella | 13,824 | 256 | 12×16 | 72 |
| Super Perfect Cella | 55,296 | 1,024 | 12×16 | 288 |
| Cella Max | 221,184 | 4,096 | 12×16 | 1,152 |

1ページの大きさは初期値です。[設定](config.ja.md)で小さくできます。クライアント側は既定でウィンドウに合わせます。

## レシピ

**Larval Cella**

```
生の牛肉    生の豚肉    生の羊肉
フグ        チェスト    生の兎肉
生のタラ    クモの目    腐った肉
```

**Imperfect Cella** — レシピなし。Larval Cella に経験値を与えます。

**Semi-Perfect Cella** — 黒曜石1個を Imperfect Cella 8個で囲みます。

**Perfect Cella** — 金ブロック1個を Semi-Perfect Cella 8個で囲みます。

**Super Perfect Cella** — レシピなし。Perfect Cella を満たしてジ・エンドで自爆させます。

**Cella Max** — 四隅に Super Perfect Cella、辺にネザースター4個、中央に不死のトーテム1個。

**Cella Jr. ×7** — Perfect Cella または Super Perfect Cella 1個をダイヤモンドブロック8個で囲みます。中央の Cella は消費されません。

レシピに使う Cella はすべて経験値が満タンである必要があります。中身は作られたものへ引き継がれますが、経験値は引き継がれません。

パーティションも名前・色・大きさ・中身ごと引き継がれます。並びはクラフト枠の順で、左から右・上から下です。一度も分けていない Cella（パーティション1つ・名前なし・最初の色・全体の大きさ）はパーティションとしては残りません。そうした Cella の中身はまとめて1つのパーティションに入り、他のパーティションの後ろに付きます。使った Cella がどれも分けられていなければ、作られたものも分けられていない状態になります。

## 育てる

スニークしながら右クリックで経験値を与えます。必要な分を一度に持っていきます。経験値は入る一方で、取り出せません。

| | 満タンまで |
|---|---:|
| Larval Cella | 30レベル |
| Imperfect Cella | 30レベル |
| Semi-Perfect Cella | 50レベル |
| Perfect Cella | 100レベル |
| Super Perfect Cella | 200レベル |

Cella Jr. と Cella Max は経験値を受け取りません。

Larval Cella は満タンになるとその場で Imperfect Cella になります。

満タンの Perfect Cella はネザースターで自爆させられます。全方向100ブロックを破壊して Super Perfect になって戻りますが、**ジ・エンドでのみ**です。他の場所では消えて終わりです。

⚠ 爆発は生き残れません。十分に離れてください。

育った Cella はパーティションを同じ大きさのまま残します。増えた分は空きになります。一度も分けていない Cella は分けていないまま、新しい大きさ全体を使います。

## 各形が耐えるもの

| | 壊したとき | 爆発耐性 | 適正ツール | 火 |
|---|---|---:|---|---|
| Larval Cella | 中身がこぼれる | 2.5 | 斧 | 燃える |
| Imperfect Cella | 中身を保持 | 6 | 鉄の斧 | — |
| Semi-Perfect Cella | 中身を保持 | 1,200 | ダイヤモンドの斧 | — |
| Cella Jr. | 中身を保持 | 1,200 | ダイヤモンドのツルハシ | — |
| Perfect Cella | 中身を保持 | 1,200 | ダイヤモンドのツルハシ | — |
| Super Perfect Cella | 中身を保持 | 3,600,000 | ネザライトのツルハシ | 燃えない |
| Cella Max | 中身を保持 | 3,600,000 | ネザライトのツルハシ | 燃えない |

Imperfect 以降は壊しても中身を保持します。アイテムが預けた中身の名前を持っていて、設置し直すと中身が戻ります。

Super Perfect Cella と Cella Max だけは、ウィザーに壊されず、アイテムの状態でも失われず（火・溶岩・サボテン・爆発・奈落のいずれでも）、死んでも手元に残ります。

検索できるのは Semi-Perfect 以降です。Larval と Imperfect にはありません。

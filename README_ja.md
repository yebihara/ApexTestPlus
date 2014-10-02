Apex Test Plus for Force.com Migration Tool
===========================================
\[[English version](README.md)\]

はじめに
--------
Apex Test Plus for Force.com Migration Toolは、Force.com Migration Toolとともに動作する、Apexプログラムのテストを容易に、かつ、高速に行うためのカスタムAntタスクです。  
このツールは、特にForce.com IDE (Eclipse)とともに利用することによりApex開発の生産性を大きく向上させることができ、TDD(Test Driven Development)の実践を支援します。  
Apex Test Plusは、Force.com Migration ToolやForce.com IDEが提供しない、次のような機能を実現します。

* テストの実行結果を保存する
* コードカバレッジ結果をビジュアル表示する
* デバッグログの、特定のテストメソッドに関連する部分のみを表示する

このリポジトリをクローン、またはダウンロードし、Webブラウザで`doc/sample_result/index.html`を開いてみてください。実際にApex Test Plusが生成したサンプルテスト結果を見ることができます。  
なお、Force.com Migration Toolについては, [Force.com Migration Tool Guide](http://www.salesforce.com/us/developer/docs/daas/index.htm)をご覧ください。


インストール
------------

1. Force.com Migration Tool(とAnt)をインストールします。現在のところ、Apex Test Plusとともに動作することが確認されているForce.com Migration Toolバージョンは、21.0から31.0です。
1. [Apache Commons Lang](http://commons.apache.org/lang/download_lang.cgi)(バージョン2.6以上)をダウンロードします。または、Apex Test Plusにバンドルされる`commons-lang-2.6.jar`を使用することもできます。
1. `commons-lang-`*version*`.jar`と`ant-apextestplus.jar`を、Antインストールディレクトリ以下の`lib/`ディレクトリ内にコピーします。


build.xmlの作成
---------------
適切なbuild.xmlを作成するもっとも簡単な方法は、Force.com Migration Toolに付属するサンプルのbuild.xmlとbuild.propertiesを利用することです。  
まずbuild.xmlを開き、projectルート要素に、**xmlns:apextestplus="antlib:com.force.jp.ant.apextestplus"**という属性設定を以下のように追加します。

    <project name="Sample usage of Salesforce Ant tasks" default="test" basedir="." xmlns:sf="antlib:com.salesforce" xmlns:apextestplus="antlib:com.force.jp.ant.apextestplus">

テスト実行のためのAntタスクは**runTest**という名前です。

以下では、多くのケースで利用可能と思われるタスク定義のサンプルをいくつか紹介します。

### 最新ソースファイルをサーバーから取得
次のようなタスクを定義すると、テスト実行直前には常に、ApexクラスとApexトリガーのソースコードがサーバーから取得されます。  
ソースコードはサーバー上で他のユーザによって改変されている可能性があるため、これはビジュアルなコードカバレッジ結果を生成するためのもっとも確実な方法です。

    <property name="outDir" value="test_result"/>
    <property name="srcDir" value="${outDir}/src"/>

    <target name="runTest" depends="cleanOutDir,bulkRetrieveClasses,bulkRetrieveTriggers">
      <apextestplus:runTest username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" srcDir="${srcDir}" outDir="${outDir}" runAllTests="false" logType="Debugonly" >
        <class>TestClass1</class>
        <class>TestClass2</class>
        <class>TestClass3</class>
      </apextestplus:runTest>
    </target>

    <target name="cleanOutDir">
      <delete dir="${outDir}" />
    </target>

    <target name="bulkRetrieveClasses">
      <mkdir dir="${srcDir}"/>
      <sf:bulkRetrieve username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" metadataType="ApexClass" retrieveTarget="${srcDir}"/>
    </target>

    <target name="bulkRetrieveTriggers">
      <mkdir dir="${srcDir}"/>
      <sf:bulkRetrieve username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" metadataType="ApexTrigger" retrieveTarget="${srcDir}"/>
    </target>

### ローカルソースファイルを利用
サーバーからソースコードを取得する方式は信頼性は高いですが、時間がかかります。  
もしあなたが自分専用のsalesforce.com環境(Develper Editionなど)を持っているのなら、ローカルPCに保存されているソースコードからビジュアルなコードカバレッジ結果を生成しても安全でしょう。  
以下の例では、Force.comプロジェクトの`src/`ディレクトリが存在するディレクトリと同じ場所にbuild.xmlが保存されている前提である点にご注意ください。

    <property name="outDir" value="test_result"/>
    <property name="srcDir" value="src"/>

    <target name="runTestLocal" depends="cleanOutDir">
      <apextestplus:runTest username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" srcDir="${srcDir}" outDir="${outDir}" runAllTests="false" logType="Debugonly" >
        <class>TestClass1</class>
        <class>TestClass2</class>
        <class>TestClass3</class>
      </apextestplus:runTest>
    </target>

    <target name="cleanOutDir">
      <delete dir="${outDir}" />
    </target>


テストの実行
------------
定義したrunTestタスクを含むターゲットをAntから実行してください。  
ビルドが正常終了すると、runTestタスクのoutDir属性で指定した場所に、テスト結果が生成されます。  
その中のindex.htmlをWebブラウザで開き、テスト結果を確認してください。


RunTestタスクリファレンス
-------------------------
**runTest**タスクはApexテストのセットを実行するために使用されます。テストクラスと、テスト対象のクラス・トリガーは事前にサーバーにデプロイされている必要があります。

<dl>
<dt>**username**</dt>

<dd>必須の属性。Salesforceへのログインに利用するユーザー名。</dd>

<dt>**password**</dt>

<dd>必須の属性。Salesforceへのログインに利用するパスワード。セキュリティトークンが必要な場合は、25桁のトークンをパスワードに続けて記述してください。</dd>

<dt>**serverurl**</dt>

<dd>オプションの属性。デフォルトは'https://login.salesforce.com'。サンドボックス(test.salesforce.com)環境で作業する場合に指定します。</dd>

<dt>**srcDir**</dt>

<dd>必須の属性。ApexクラスとApexトリガーのソースコードが保存される、`classes/`ディレクトリと`triggers/`ディレクトリ が存在するディレクトリ。
これらのソースコードを色づけすることにより、ビジュアルなコードカバレッジ結果が生成されます。
</dd>

<dt>**outDir**</dt>

<dd>必須の属性。テスト結果ファイルが保存されるディレクトリ。</dd>

<dt>**coverageTarget**</dt>

<dd>オプションの属性。デフォルトは75。目標とするコードカバレッジ率。実際のカバレッジ率がこの値に満たない場合、テスト結果内のカバレッジ数値は赤く色づけされます。</dd>

<dt>**runAllTests**</dt>

<dd>オプションの属性(true/false)。デフォルトはtrue。trueを指定すると、デプロイ済みのすべてのApexテストが実行されます。 </dd>

<dt>**logType**</dt>
<dd>オプションの属性。デフォルトは'None'。テスト中に生成されるデバッグログのレベル。有効な値は'None', 'Debugonly', 'Db', 'Profiling', 'Callout', 'Detail'のいずれか。</dd>

<dt>**class**</dt>

<dd>オプションの子要素。実行するApexテストクラスのリスト。runAllTests="false"のときは、少なくとも1つの要素を指定しなければなりません。テストクラスは事前にデプロイされている必要があります。各要素には"test"という名前の属性を指定することもできます(true/false, デフォルトはtrue)。`test="false"`とされたクラスは無視され、テストは実行されません。</dd>


Force.com IDEとの連携
---------------------
Force.com Test PlusをForce.com IDE (Eclipse)と連携させるには、最初にEclipseのAntアドオンの設定を行う必要があります。

1. "設定"ウィンドウを開きます。(Windowsなら[ウィンドウ] > [設定]、Macなら[Eclipse] > [環境設定])
1. 左側ツリーから [Ant] > [ランタイム] を開き、"クラスパス"タブを選択します。
1. 次の3つのJARファイルをグローバル項目に追加し、OKを押します。
    * ant-salesforce.jar (Force.com Migration Tool)
    * ant-apextestplus.jar
    * commons-lang-<em>version</em>.jar

build.xmlとbuild.propertiesは目的のForce.comプロジェクトのルートディレクトリに保存します。

以上の手順の完了後、次のようにしてテストを実行することができます。

1. パッケージエクスプローラに表示されているbuild.xmlを右クリックします。
1. [実行] > [2 Antビルド...] を選択します。
1. 目的のrunTestタスクを含むターゲットを選択します。
1. "実行"を押すと、テストが開始されます。

同じテストの再実行は、ツールバーの外部ツールアイコンをクリックするだけでできます。

cd ..
mvn -Pdistro clean install -DskipTests

deploy时 配置
    <url>http://nexus.17usoft.com/repository/mvn-all/</url>
      <mirrorOf>central</mirrorOf>
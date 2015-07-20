# Tableau Server - LDAP Synchronization

TabSync is a bi-directional replication tool between Tableau Server 9.0+ and any LDAP server 

## Installation

* Download the certificate of your Tableau Server

* Make sure you have Java 1.8 (or higher) installed on your system, as it is a pre-requisite to import certificates

* Import the certificate:


    $ cd C:\Program Files\Java\jdk1.8.0_45\jre\lib\security
    $ ..\..\bin\keytool -import -alias mycertificate -file c:\Users\JohnDoe\Downloads\mycertificate_company_com.crt  -keystore cacerts

* LDAP Changes:

* Create an uberjar from the source:

    $ git clone https://github.com/starschema/tabsync.git
    $ cd tabsync
    $ lein uberjar

* Create a directory called 'config' in the root of your newly created jar executable, and make sure to place a file called 'groups.yml' under the same directory. Make sure to follow the formatting pattern:

    ldap:
      host: ldap.domain.com:389
      username: administrator
      password: administrator
    ad:
      domain: local
    email:
     from: tableau.sync.script@company.com
     to: john.doe@company.com
     subject: Tableau Sync Script Report
    tableau:
      url: "http://127.0.0.1:8000/"
      version: 9
      username: tableauadmin
      password: password
    sites:
    - name: Site1
      group_mapping:
        - ldap: grp12345
          tableau: LDAP Group 1
    - name: Site2
      group_mapping:
        - ldap: grp54321
          tableau: LDAP Group 2

Once the config file is saved you need to make sure that you are pointing to the right java installation:

    $ java -version
    java version "1.8.0_31"
    Java(TM) SE Runtime Environment (build 1.8.0_31-b13)
    Java HotSpot(TM) 64-Bit Server VM (build 25.31-b07, mixed mode)

If it is not pointing to Java version 1.8 then you need to set it:

## Windows

    $ SET JAVA_HOME = c:\Program Files\Java_1.8
    $ SET PATH = %PATH%;%JAVA_HOME%\bin

## Linux

    $ export JAVA_HOME=/usr/bin/java
    $ export PATH=$PATH:$JAVA_HOME/bin

And finally simply execute the uberjar you created earlier:



    $ java -jar tabsync-0.1.0-standalone.jar [args]

## Usage

FIXME: explanation

## Options

FIXME: listing of options this app accepts.

## Examples

```clojure
(defn test-syntax
  "testing syntax"
  [parameters]
  (println parameters))
```
...

### Bugs



...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

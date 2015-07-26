# Tableau Server - LDAP Synchronization [![Build Status](https://travis-ci.org/starschema/tabsync.svg?branch=master)](https://travis-ci.org/starschema/tabsync)

TabSync is a bi-directional replication tool between Tableau Server 9.0+ and any LDAP server. It synchronizes LDAP groups with Tableau groups. 

 * Synchronizes multiple ldap groups with multiple tableau groups on multiple sites
 * Adds all users to tableau site who are defined in ldap but not existing in tableau
 * Synchronizes each ldap group with the corresponding tableau group (adds, deletes users according to actual ldap memberships)
 * Sets domain to users to be able to use Active Directory authentication after synchronization (optional)

It can be deployed as single standalone JAR file without any interpreter dependency (with `lein uberjar`).


## Installation

* Download the certificate of your Tableau Server (optional, in case of https)

* Make sure you have Java 1.8 (or higher) installed on your system, as it is a pre-requisite to import certificates. Older java version might throw SSL exceptions for some SSL certificates (optional, in case of https)

* Import the certificate:

<b></b>

    $ cd C:\Program Files\Java\jdk<version>\jre\lib\security
    $ ..\..\bin\keytool -import -alias mycertificate -file c:\Users\JohnDoe\Downloads\mycertificate_company_com.crt  -keystore cacerts

* LDAP Configuration Changes:

The bind-dn format listed in the config file is specific to our environment and is recommended to be modified to your format. We also suggest modifying the functions 'tabsync.ldap/get-user-info' and 'tabsync.ldap/get-users-from-group'. The former for parsing a full name & email map, and the latter for parsing the user list based on group id(s).

<b></b>

* Create an uberjar from source:

<b></b>


    $ git clone https://github.com/starschema/tabsync.git
    $ cd tabsync
    $ lein uberjar

## Usage


* Create a directory called 'config' in the root of your newly created jar executable, and make sure to place a file called 'groups.yml' under the same directory. Make sure to follow the formatting pattern:

<b></b>

    ldap:
      host: ldap.domain.com:389
      username: "cn=administrator,ou=Sample,dc=AD,dc=company,dc=com"
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

* Make sure that you are pointing to the right Java installation:

<b></b>

    $ java -version
    java version "1.8.0_31"
    Java(TM) SE Runtime Environment (build 1.8.0_31-b13)
    Java HotSpot(TM) 64-Bit Server VM (build 25.31-b07, mixed mode)

If it is not pointing to Java version 1.8 then you need to set it:

### Windows

    $ SET JAVA_HOME = c:\Program Files\Java_1.8
    $ SET PATH = %PATH%;%JAVA_HOME%\bin

### Linux

    $ export JAVA_HOME=/usr/bin/java
    $ export PATH=$PATH:$JAVA_HOME/bin

And finally simply execute the uberjar you created earlier:

    $ java -jar tabsync-0.1.0-standalone.jar [args]


## Examples

You should modify two functions in order to use your own LDAP schema for getting users from a group and their user info. 

_Example 1_: Get detailed user info. Use `employeNumber` ldap attribute as username, `displayName` as full name, `email` as email.  

```clojure
(defn get-user-info
  "Gets the user's SSO & email address"
  [sso]
  (let
    [user-info (ldap/get ldap-server (str "CN=" sso ",ou=All Businesses,dc=CDIAD,dc=corporate,dc=com"))]
    (log/debug user-info)
    {:sso (get user-info :employeeNumber) :name (beautify-display-name (get user-info :displayName))   :mail (get user-info :mail)}))
```


_Example 2_: Get users from group. First search for group in `OU=Groups` as `CN=group-id`, then take the first nine letters from the returned CNs. 

```clojure
(defn get-users-from-group
  "Gets the list of users associated to each LDAP group"
  [group-id]
  (log/info "Getting users for ldap group " group-id)
  (let
    [group-info (ldap/get ldap-server (str "CN=" group-id ",OU=Groups,DC=CDIAD,dc=corporate,dc=com"))]
      (->>
        (get group-info :member)
        (list)
        (flatten)
        (map #(second (re-find #"CN=([a-zA-Z0-9]+{9})" %)))
        (map (fn [cn]
               (if (= (get cn 0) \g)
                 (get-users-from-group cn)
                 cn )))
        (flatten)
        (distinct))))
```

Feel free to change these parts according to your LDAP scheme. 

## License

Copyright © 2015 Starschema Ltd

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

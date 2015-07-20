# Tableau Server - LDAP Synchronization

TabSync is a bi-directional replication tool between Tableau Server 9.0+ and any LDAP server 

## Installation

1) Download the certificate of your Tableau Server

2) Make sure you have Java 1.8 (or higher) installed on your system, as it is a pre-requisite to import certificates

3) Import the certificate:

cd C:\Program Files\Java\jdk1.8.0_45\jre\lib\security
..\..\bin\keytool -import -alias mycertificate -file c:\Users\JohnDoe\Downloads\mycertificate_at_domain_com.crt  -keystore cacerts

## Usage

FIXME: explanation

    $ java -jar tabsync-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Examples

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

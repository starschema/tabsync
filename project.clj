(defproject tabsync "0.2.0-SNAPSHOT"
  :description "Tableau-LDAP Synchronization"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-yaml  "0.4.0"]
                 [clj-http "1.1.0"]
                 [clj-tableau "1.0.2-SNAPSHOT"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.draines/postal "1.11.3"]
                 [org.clojars.pntblnk/clj-ldap "0.0.9"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jdmk/jmxtools
                                                    com.sun.jmx/jmxri]]]
  :main ^:skip-aot tabsync.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

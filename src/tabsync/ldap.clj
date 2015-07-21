(ns tabsync.ldap
  (:require [clj-ldap.client :as ldap])
  (:require [clojure.tools.logging :as log])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.string :as str]))

(defn connect-ldap-server
  "Creates connection to LDAP server"
  [[host username password]]
  (def ldap-server
    (ldap/connect {:host     host
                   :bind-dn  username
                   :password password
                   })))

(defn beautify-display-name
  "This function removes LDAP specific display name suffix"
  [fullname]
  (str/trim (first (str/split fullname #"\(") )))

(defn get-user-info
  "Gets the user's SSO & email address"
  [sso]
  (let
    [user-info (ldap/get ldap-server (str "CN=" sso ",ou=All Businesses,dc=CDIAD,dc=corporate,dc=com"))]
    (log/debug user-info)
    {:sso (get user-info :employeeNumber) :name (beautify-display-name (get user-info :displayName))   :mail (get user-info :mail)}))

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

(defn get-ldap-group-names
  "This function parses the ldap group names from yaml"
  [config-file]
  (map (fn [entry] (get entry :ldap))
    (get (yaml/parse-string (slurp config-file)) :group_mapping)))


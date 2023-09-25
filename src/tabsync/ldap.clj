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

;; (defn get-users-from-group
;;   "Gets the list of users associated to each LDAP group"
;;   [group-id]
;;   (log/info "Getting users for ldap group " group-id)
;;   (let
;;     [group-info (ldap/get ldap-server (str "CN=" group-id ",OU=Groups,DC=CDIAD,dc=corporate,dc=com"))]
;;       (->>
;;         (get group-info :member)
;;         (list)
;;         (flatten)
;;         (map #(second (re-find #"CN=([a-zA-Z0-9]+{9})" %)))
;;         (map (fn [cn]
;;                (if (= (get cn 0) \g)
;;                  (get-users-from-group cn)
;;                  cn )))
;;         (flatten)
;;         (distinct))))

(defn get-ldap-group-names
  "This function parses the ldap group names from yaml"
  [config-file]
  (map (fn [entry] (get entry :ldap))
    (get (yaml/parse-string (slurp config-file)) :group_mapping)))



(defn get-users-from-group
  "Gets the list of users associated to each LDAP group"

  ;; Single argument is the top-level thing to call and returns a list
  ([group-id]
    (log/info "[LDAP] Starting to fetch full members list for group:" group-id)
    (apply list (get-users-from-group group-id #{} #{})))

  ;; Three-arg version is for the recursion and returns a set
  ([group-id member-list group-list]
    (log/info "[LDAP] Getting users for ldap group " group-id)
    (let [group-info (ldap/get ldap-server (str "CN=" group-id ",OU=Groups,DC=CDIAD,dc=corporate,dc=com"))
            ;; create a list of CNs from the response
            cn-groups (->> (get group-info :member)
                        (list)
                        (flatten)
                        (map #(second (re-find #"CN=([a-zA-Z0-9]+{9})" %)))
                        (group-by #(= (get % 0) \g)))

            ;; the members of the current group
            members (get cn-groups false)
            ;; the sub-groups of the current group
            groups (get cn-groups true)
            ;; find the groups that aren't yet processed
            groups-to-add (filter #(not (contains? group-list %)) groups)
            ;; build a list of groups we've already seen to not process them again
            new-group-list (clojure.set/union group-list (set groups))

            ; combine the members with the function input members list to start the call
            initial-member-list (clojure.set/union member-list (set members))]

            (log/info "[LDAP:" group-id "] Found " (count members) " members in this group, total member count is at " (count initial-member-list ))
            (log/info "[LDAP:" group-id "] Found " (count groups) " sub-groups, adding " (count groups-to-add) " sub-groups after duplicate checks:" groups-to-add)
            (reduce
              (fn [current-member-list group-to-add]
                  (get-users-from-group group-to-add current-member-list new-group-list))
              initial-member-list groups-to-add))))


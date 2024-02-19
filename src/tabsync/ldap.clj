(ns tabsync.ldap
  (:require [clj-ldap.client :as ldap])
  (:require [clojure.tools.logging :as log])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.string :as str]))

;; Setup functions
;; ---------------

;; These are global because (due to the original architecture of the tool) they are
;; used by both the `core` and the `tableau` module, and only `core` has access to the `config`
;;
;; TODO: rectify this by packaging LDAP config and state into a data structure


(defn connect-ldap-server
  "Creates connection to LDAP server"
  ;; TODO: move this to key-based destructuring
  [[host ssl username password]]
  (log/info "SSL enabled: " ssl)
  (def ldap-server
    (ldap/connect {:host     host
                   :ssl?     ssl
                   :bind-dn  username
                   :password password
                   })))


(defn setup-ldap-queries
  "Attempts to add merge the queries from the config file with the default ones"
  [{group :group
    user :user
    :or {group "CN=$$$,ou=Groups,dc=cdiad,dc=ge,dc=com"
         user  "CN=$$$,OU=All Businesses,DC=CDIAD,DC=GE,DC=com"}}]

  (def ldap-queries {:group group :user user}))

;; Query helpers
;; -------------

(defn execute-ldap-query
  "Executes (and potentially logs) an LDAP query"
  [ldap-server query]
    ;; log the query
    ;; TODO: only if needed
    (log/info "[LDAP] running query:" query)

    ;; run the query
    (let [response (ldap/get ldap-server query)]
      ;; log the result
      ;; TODO: only log if needed
      (log/info "[LDAP] response:" response)
      ;; return the response
      response))

(defn template-ldap-query
  "Templates a query by replacing instances of '$$$' with the value of `value`"
  [query value]
  ;; This is a simple string replacement
  ;; if multiple tokens are needed, a dict can be used
  ;; see https://clojuredocs.org/clojure.string/replace#example-542692d7c026201cdc327100
  (clojure.string/replace query #"\$\$\$" value))


(defn execute-templated-ldap-query
  "Templates and executes an LDAP query (see execute-ldap-query and template-ldap-query)"
  [ldap-server query value]
  (execute-ldap-query ldap-server (template-ldap-query query value)))


;; User queries
;; ------------

(defn beautify-display-name
  "Removes specific display name suffix"
  [fullname]
  (str/trim (first (str/split fullname #"\("))))

(defn get-user-info
  "Gets the user's SSO & email address"
  [sso]
  (let
    ;; [user-info (ldap/get ldap-server (str "CN=" sso ",ou=All Businesses,dc=CDIAD,dc=GE,dc=com"))]
    [user-info (execute-templated-ldap-query ldap-server (get ldap-queries :user) sso)]
    (log/debug user-info)
    {:sso  (get user-info :employeeNumber)
     :name (beautify-display-name (or (get user-info :displayName) (get user-info :geFullName) "Unknown"))
     :mail (or (get user-info :mail) (str sso "@mail.ad.ge.com"))}))

(defn get-ldap-group-names
  "Parses the ldap group names from yaml"
  [config-file]
  (map (fn [entry] (get entry :ldap))
       (get (yaml/parse-string (slurp config-file)) :group_mapping)))

;; Group query
;; -----------

(defn get-users-from-group
  "Gets the list of users associated to each LDAP group"

  ;; Single argument is the top-level thing to call and returns a list
  ([group-id]
    (log/info "[LDAP] Starting to fetch full members list for group:" group-id)
    (apply list (first (get-users-from-group group-id #{} #{}))))

  ;; Three-arg version is for the recursion and returns two sets
  ([group-id member-list group-list]
    (log/info "[LDAP] Getting users for ldap group " group-id)
    (if (contains? group-list group-id)
      ; If already processed this group then skip it
      (do
        (log/info "[LDAP:" group-id "] Group already processed, so skipping ")
        [member-list group-list])

      ; otherwise process
      (try
        (let [  group-info (execute-templated-ldap-query ldap-server (get ldap-queries :group) group-id)
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

                ; combine the members with the function input members list to start the call
                initial-member-list (clojure.set/union member-list (set members))]

                (log/info "[LDAP:" group-id "] Found " (count members) " members in this group, total member count is at " (count initial-member-list ))
                (log/info "[LDAP:" group-id "] Found " (count groups) " sub-groups, adding " (count groups-to-add) " sub-groups after duplicate checks:" groups-to-add)
                (reduce
                  (fn [[current-member-list current-group-list] group-to-add]
                      (get-users-from-group group-to-add current-member-list current-group-list))
                  ;; init with the current full group list and initial member list
                  [initial-member-list (clojure.set/union group-list #{group-id})]
                  groups-to-add))

        (catch Exception e
              (log/debug "Exception occured:" (with-out-str (clojure.stacktrace/print-cause-trace e)))
              ;(log/error (type e) ": " (.getMessage e))
              (log/error "DL not processed or it may not exist: " group-id " -- error: " (take 50 (clojure.string/split-lines (with-out-str (clojure.stacktrace/print-cause-trace e)))))
              [member-list group-list]
              )))

              ))
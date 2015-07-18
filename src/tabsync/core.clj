(ns tabsync.core
  (:gen-class)
  (:require [clojure.tools.logging :as log])
  (:require [clj-yaml.core :as yaml])
  (:require [clj-ldap.client :as ldap])
  (:require [clojure.string :as str])
  (:require [clojure.data])
  (:require [clj-tableau.restapi])
  (:require [postal.core])
  (:require [tabsync.tableau :as tableau]
            [clj-tableau.restapi :as tapi])
  (:use [tabsync.ldap]))

;; Constant for yaml configuration file
(def yaml-file "./config/groups.yml")

(def config-vars (yaml/parse-string (slurp yaml-file)))

(defn send-email
  [email-params]
  (postal.core/send-message {:host "localhost"}
                            (merge email-params
                                   {:body
                                    (str
                                      "Synchronization finished.\r\nPlease find the list of warning and error entries:\r\n\r\n"
                                      (slurp "log/email.asc"))
                                    })))


(defn get-ldap-groups
  "Parses the yaml configuration file and gets the ldap groups"
  [group-mapping]
  (map #(get % :ldap) group-mapping))

(defn get-tableau-groups
  "Parses the yaml configuration file and gets the tableau groups"
  [group-mapping]
  (map #(get % :tableau) group-mapping))

(defn return-ldap-users
  [ldap-group]
  (tabsync.ldap/get-users-from-group ldap-group))

(defn return-tableau-users
  [session tableau-group]
  (log/info "Getting users for TABLEAU group: " tableau-group)
  (tableau/get-users-from-tableau-group session tableau-group))

(defn synchronize-site
  "This function creates a list for tableau and a list for ldap groups.
  It also zips the two groups together"
  [site]
  (log/info "Starting with site: " (get site :name))
  (let [tableau-session (clj-tableau.restapi/logon-to-server
                          (get-in config-vars [:tableau :url])
                          (get site :name)
                          (get-in config-vars [:tableau :username])
                          (get-in config-vars [:tableau :password])
                          )]
    (tabsync.tableau/populate-user-list-with-site-users tableau-session)
    (dorun
      (map
        (fn [group]
          (let [difference
                (clojure.data/diff
                  (set (return-tableau-users tableau-session (get group :tableau)))
                  (set (return-ldap-users (get group :ldap)))
                  )]
            (log/debug "Group differences " difference)
            ;(tabsync.tableau/remove-users-from-group
            ;  tableau-session
            ;  (get group :tableau)
            ;  (first difference))
            (tabsync.tableau/add-users-to-site-and-group
              tableau-session
              (get group :tableau)
              (second difference))))
        (get site :group_mapping)))
    (tapi/signout tableau-session)))



(defn get-tableau-sites
  "Parses the yaml configuration file and gets the tableau site names accordingly"
  [config]
  (map synchronize-site (get config :sites)))

(defn -main
  [& args]
  (log/info "Parsing configuration..")
  (try
    (connect-ldap-server (vec (map val (get config-vars :ldap))))

    (dorun (get-tableau-sites config-vars))

    (log/info "Synchronization completed")

    (catch clojure.lang.ExceptionInfo e
      (log/error e)
      (log/debug "Critical problem " (ex-data e))
      )
    (catch Exception e
      (log/error (.getMessage e))
      (log/debug (.getStackTrace e))
      )
    )

  (log/debug "Sending out email")
  (send-email (get config-vars :email))
  ;(shutdown-agents)
  )

;(-main)

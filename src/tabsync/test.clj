(ns tabsync.test
    (:require [clojure.set])
    (:require [clojure.tools.logging :as log])
    )

;; (def res-list (clojure.string/split res-test))

;; (defn get-results [group-id]
;;     (get { "g0"        {:member ["yoloHello,CN=123456789" "bela,CN=12345678A" "sanyi,CN=g12345678"]}
;;            "g12345678" {:member ["Karim,CN=AB3456789" "bela,CN=12345678A" "sanyi,CN=g0,LOFASZ" "maki,CN=gAB345678"]}
;;            "gAB345678" {:member ["JOCI,CN=FE3456789" "bela,CN=12345678A" "yoloHello,CN=123456789" "sanyi,CN=g0,XXX" "sanyi,CN=g12345678"]}
;;            } group-id))

;; (defn get-test-users-from-group
;;   "Gets the list of users associated to each LDAP group"
;;   ;; Single argument is the top-level thing to call and returns a list
;;   ([group-id]
;;     (log/info "[LDAP] Starting to fetch full members list for group:" group-id)
;;     (apply list (get-test-users-from-group group-id #{} #{})))
;;   ;; Three-arg version is for the recursion and returns a set
;;   ([group-id member-list group-list]
;;     (log/info "[LDAP] Getting users for ldap group " group-id)
;;     (let [group-info (get-results group-id)
;;             ;; create a list of CNs from the response
;;             cn-groups (->> (get group-info :member)
;;                         (list)
;;                         (flatten)
;;                         (map #(second (re-find #"CN=([a-zA-Z0-9]+{9})" %)))
;;                         (group-by #(= (get % 0) \g)))

;;             ;; the members of the current group
;;             members (get cn-groups false)
;;             ;; the sub-groups of the current group
;;             groups (get cn-groups true)
;;             ;; find the groups that aren't yet processed
;;             groups-to-add (filter #(not (contains? group-list %)) groups)
;;             ;; build a list of groups we've already seen to not process them again
;;             new-group-list (clojure.set/union group-list (set groups))

;;             ; combine the members with the function input members list to start the call
;;             initial-member-list (clojure.set/union member-list (set members))]

;;             (log/info "[LDAP:" group-id "] Found " (count members) " members in this group, total member count is at " (count initial-member-list ))
;;             (log/info "[LDAP:" group-id "] Found " (count groups) " sub-groups, adding " (count groups-to-add) " sub-groups after duplicate checks:" groups-to-add)
;;             (reduce
;;             (fn [current-member-list group-to-add]
;;                 (get-test-users-from-group group-to-add current-member-list new-group-list))
;;             initial-member-list groups-to-add)
;;         )))


;; ;; (defn -main []
;; ;;     (pr (get-test-users-from-group "g0"))
;; ;; )
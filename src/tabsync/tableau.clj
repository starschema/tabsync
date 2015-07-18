(ns tabsync.tableau
  (:require [clj-tableau.restapi :as tapi]
            [tabsync.ldap]
            [clojure.tools.logging :as log])
  (:import (clojure.lang ExceptionInfo)))

(def user-list (atom '{}))

(defn populate-user-list-with-site-users
  [session]
  (log/info "Populate user list")
  (reset! user-list (tapi/get-users-on-site session))
  (log/info "Found" (count @user-list) "users"))

(defn does-user-exist?
  [session username]
  (log/debug "checking if user " username " exists on site" (get session :content-url))
  (get @user-list username))

(defn add-users-to-site-and-group
  [session group users]
  (log/debug "add-users-to-site-and-group" group " -> " users)
  (let [group-id (tapi/get-group-id session group)]
    (if-not (nil? group-id)
      (dorun
        (->>
          (doall (pmap
                   (fn [user]
                     (log/debug "Checking user: " user)
                     (if (does-user-exist? session user)
                       ; user does exists
                       (do
                         (log/debug "user " user " exists on site " (get session :content-url))
                         (get @user-list user))
                       ; we need to add the user to the site
                       (do
                         (log/debug "user" user "does not exist on this site")
                         (let [user-id (tapi/add-user session user)
                               user-info (tabsync.ldap/get-user-info user)]
                           (if-not user-id
                             ; user was added just now?
                             ; TODO: check again
                             (get @user-list user)
                             ; User created successfully
                             (do
                               (tapi/update-user session user-id (get user-info :name) (get user-info :mail))
                               user-id))))))
                   users))
          (remove nil?)
          (pmap (fn [user]
                  (tapi/add-user-to-tableau-group session group-id user)))))
      (log/error "Unable to add user to group. Please check group-id for " group))))

(defn remove-users-from-group
  [session group users]
  (let [group-id (tapi/get-group-id session group)]
    (doall
      (map (fn [user]
             (if user
               (do
                 (log/debug "Removing user " user " with id " (get @user-list user) " from group " group)
                 (tapi/remove-user-from-tableau-group
                   session group-id (get @user-list user))))) users))))

(defn get-users-from-tableau-group
  [session group]
  (let [groupid (tapi/get-group-id session group)]
    (if groupid
      (tapi/get-users-from-group session groupid)
      (log/error "Unable to get group ID. Please double check group name."))))

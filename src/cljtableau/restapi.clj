(ns clj-tableau.restapi
  (:require [clj-http.client]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr text]]
            [clojure.data.xml :as xml]
            [clojure.tools.logging :as log])
  (:import (clojure.lang ExceptionInfo)))

(def ^:dynamic page-size 1000)

(defn- tableau-url-for
  "Constructs server API url. Target can be hostname or session returned from logon-to-server"
  [target api-path]
  (if (= (type target) java.lang.String)
    ; connect to host
    (str target "/api/2.0" api-path)
    ; connect to already established session (target )
    (str (get target :host) "/api/2.0"
         (if (= api-path "/auth/signout")
           api-path
           (str "/sites/" (get target :siteid) "/" api-path)))))

(defn- get-zip
  "Get xml-zip from http.client response"
  [http-response]
  (if (get http-response :body)
    (-> (get http-response :body)
        xml/parse-str
        xml-zip)))

(defn- get-status-from-http-exception
  "Get HTTP exception code from a clojure exception info throwed by clj-http"
  [e]
  (get (ex-data e) :status))

(defn- http
  "Perform a http call to tableau server. Host can be hostname or session"
  [method host api-path http-params]
  (get-zip
    ((resolve (symbol (str "clj-http.client/" method)))
      (tableau-url-for host api-path)
      (merge http-params {:headers {"X-Tableau-Auth" (get host :token)}}))))

(defn- logindata
  "Creates XML request for logon call"
  [site name password userid]
  (let [request-body
        (xml/emit-str
          (xml/element :tsRequest {}
                       (xml/element :credentials {:name     name
                                                  :password password}
                                    (when userid (xml/element :user {:id userid}))
                                    (xml/element :site {:contentUrl site}))))]
    (log/debug request-body)
    request-body
    ))

(defn- get-users-from-tableau-response
  [ts-response]
  (xml-> ts-response :users :user (juxt (attr :id) (attr :name))))

(defn- updateuserdata
  "Creates XML request for user update method"
  [fullname email]
  (xml/emit-str
    (xml/element :tsRequest {}
                 (xml/element :user {
                                     :fullName fullname
                                     :email    email
                                     }))))

(defn- adduserdata
  "Creates XML request for user creation"
  [name site-role]
  (xml/emit-str
    (xml/element :tsRequest {}
                 (xml/element :user {:name     name
                                     :siteRole site-role
                                     }))))

(defn- add-or-remove-user-from-groupdata
  "Creates XML request for add & remove user methods"
  [userid]
  (xml/emit-str
    (xml/element :tsRequest {}
                 (xml/element :user {
                                     :id userid
                                     }))))

(defn delete-user-from-site
  "Removes user from site entirely
  DELETE /api/api-version/sites/site-id/users/user-id"
  [session userid]
  (http "delete" session (str "/users/" userid) {}))

(defn update-user
  "Update users email and fullname "
  [session userid fullname email]
  (http "put" session (str "/users/" userid) {:body (updateuserdata fullname email)}))


(defn add-user
  "Adds user to the site. If user already exist, do nothing, otherwise raise exception"
  [session name site-role]
  (try
    (let [ts-response (http "post" session "/users/"
                            {:body (adduserdata name site-role)})]
      (log/debug ts-response)
      (xml1->
        ts-response
        :user (attr :id)))
    (catch ExceptionInfo e
      (log/debug "Exc: " e)
      (if (= (get-status-from-http-exception e) 409)
        (log/debug "User " name " already added to this site, ignoring request.")
        (throw e)))))

(defn logon-to-server
  "Logon to tableau server by invoking /auth/signin, returns map with token,
  site id and hostname"
  [host site name password & [userid-to-impersonate]]
  (let [ts-response (http "post" host "/auth/signin"
                          {:body (logindata site name password userid-to-impersonate)})]
    (log/debug ts-response)
    {:token       (xml1-> ts-response
                          :credentials (attr :token)
                          )
     :content-url (xml1-> ts-response
                          :credentials
                          :site (attr :contentUrl)
                          )
     :siteid      (xml1-> ts-response
                          :credentials
                          :site (attr :id))
     :host        host}))

(defn signout
  "Logoff from server"
  [session]
  (http "post" session "/auth/signout" {}))

(defmacro with-tableau-rest-api
  "Initialized a new tableau session and ensures that session will be terminated when
   the processing goes out of scope. Usage:
   (with-tableau-rest-api [conn '(host site name password)] ( rest-call(conn p1 p2 )) "
  [bindings & body]
  (let [form (bindings 0) params (bindings 1)]
    `(let [~form (apply logon-to-server ~params)]
       (try
         ~@body
         (finally
           (signout ~form))))))

(defn- get-paginated-resource
  "Get all element from a paginated resource using page-size"
  [session resource collector reducer]
  (loop [page-number 1 all-elements '()]
    (let [ts-response (http "get" session (str "/" resource "/")
                            {:query-params {:pageSize   page-size
                                            :pageNumber page-number}})
          collection (conj all-elements (collector ts-response))]

      (if (xml1-> ts-response
                  :pagination)
        (if (>= (* page-size page-number) (read-string (xml1-> ts-response
                                                               :pagination
                                                               (attr :totalAvailable))))
          (reducer collection)
          (recur (inc page-number) collection))))))


(defn get-users-on-site
  "Iterates on site users defined by session."
  [session]
  (get-paginated-resource session "users" get-users-from-tableau-response
                          (fn [userid-name-pairs]
                            (->> userid-name-pairs
                                 (flatten)
                                 (apply hash-map)))))


(defn get-users-from-group
  "Iterates on site users defined by session."
  [session group-id]
  (get-paginated-resource session (str "groups/" group-id "/users") get-users-from-tableau-response
                          (fn [userid-name-pairs]
                            (->> userid-name-pairs
                                 (flatten)
                                 (apply hash-map)))))

(defn get-groups-on-site
  "Get all group ids and names from the site"
  [session]
  (get-paginated-resource session "groups"
                          (fn [response]
                            (xml-> response
                                   :groups
                                   :group
                                   (juxt (attr :id) (attr :name))))
                          (fn [groupid-name-pairs]
                            (apply hash-map (flatten groupid-name-pairs)))))

(defn get-group-id
  "Return the group-id of a specific group
  GET /api/api-version/sites/site-id/groups"
  [session group]
  (->> (get-groups-on-site session)
       (filter #(= group (second %)))
       (first)
       (first)))

(defn add-user-to-tableau-group
  "Add users to Tableau group
  POST /api/api-version/sites/site-id/groups/group-id/users/"
  [session group-id user-id]
  (log/debug (str "Adding user " user-id " to group " group-id))
  (try
    (http "post" session (str "groups/" group-id "/users")
          {:body (add-or-remove-user-from-groupdata user-id)})
    (catch ExceptionInfo e
      (log/debug "Exc: " e)
      (if (= (get-status-from-http-exception e) 409)
        (log/debug "User " name " already added to this group, ignoring request.")
        (throw e)))))

(defn remove-user-from-tableau-group
  "Removes user from Tableau group
  DELETE /api/api-version/sites/site-id/groups/group-id/users/user-id"
  [session group-id user-id]
  (try
    (if (and group-id user-id)
      (http "delete" session (str "groups/" group-id "/users/" user-id) {}))
    (catch ExceptionInfo e
      (log/error (type e) ": Error when removing user " user-id " " (ex-data e))
      (throw e))))

(defn query-user-on-site
  "Get name, role, email, full name from tableau user
  GET /api/api-version/sites/site-id/users/user-id"
  [session user-id]
  (let [ts-response (http "get" session (str "users/" user-id) {})]
    (zipmap
      ["id" "name" "siteRole" "lastLogin" "fullName"]
      (xml->
        ts-response
        :user
        (juxt (attr :id) (attr :name) (attr :siteRole) (attr :lastLogin) (attr :fullName))))))

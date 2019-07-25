(ns keycloak.integration.cookie
  (:require [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre :refer-macros [log  trace  debug  info  warn  error  fatal  report spy get-env]]
            [cljs.reader :refer [read-string]])
  (:import goog.net.cookies))

(defn get-cookie
  ([key]
   (get-cookie key nil))
  ([key not-found]
   (let [cookies goog.net.cookies
         key (str key)]
     (if (.containsKey cookies key)
       (read-string (.get cookies key))
       not-found))))

(defn set-cookie!
  "sets a cookie, the max-age for session cookie
   following optional parameters may be passed in as a map:
   :max-age - defaults to -1
   :path - path of the cookie, defaults to the full request path
   :domain - domain of the cookie, when null the browser will use the full request host name
   :secure? - boolean specifying whether the cookie should only be sent over a secure channel
   :raw? - boolean specifying whether content should be stored raw, or as EDN "
  [k content & [{:keys [max-age path domain secure? raw?]} :as opts]]
  (let [content (if raw?
                  (str content)
                  (pr-str content))]
    (if-not opts
      (.set goog.net.cookies k content)
      (.set goog.net.cookies k content (or max-age -1) path domain (boolean secure?)))))

(defn remove! [key]
  (.remove goog.net.cookies (str key)))

(comment 
  (re-frame/reg-cofx
   :cookie
   (fn cookie-coeffect-handler
     [{:keys [db] :as cofx} key]
     (info "cookie-coeffect-handler" key)
     (assoc cofx :cookie {key (.get goog.net.cookies key)}))))

(defn reg-cookie-fx [base-domain]
  (re-frame/reg-fx
   :cookie
   (fn cookie-fx-handler
     [cookies]
     (info "cookie-fx-handler" cookies base-domain)
     (doseq [[k v] cookies] ;be sure to set the base domain for the cookie to allow the cookie sharing between subdomains (like account.electre.com and diffusion.electre.com)
       (set-cookie! (str k) v
                    {:domain base-domain
                     :raw? true})))))

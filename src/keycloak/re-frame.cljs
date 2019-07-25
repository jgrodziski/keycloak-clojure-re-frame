(ns keycloak.integration.re-frame
  (:require [keycloak.frontend :as frontend :refer [check-to-update-token]]
            [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx inject-cofx dispatch dispatch-sync]]
            [day8.re-frame.http-fx :refer [http-effect]]
            [taoensso.timbre :as timbre :refer-macros [log  trace  debug  info  warn  error  fatal  report
                                                       logf tracef debugf infof warnf errorf fatalf reportf
                                                       spy get-env]]))

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

(defn dissoc-unused-roles [keycloak-info]
  (update-in keycloak-info [:token-parsed :realm_access :roles] (partial remove #(= "uma_authorization" %)) ))

(reg-event-fx ::keycloak-info-retrieved
 [(inject-cofx :cookie)]
 (fn [{:keys [db]} [_ {:keys [username] :as keycloak-info}]]
   (let [keycloak-info-without-unused-roles (dissoc-unused-roles keycloak-info)
         role (first (get-in keycloak-info-without-unused-roles [:token-parsed :realm_access :roles]))]
     (info "Common: keycloak info retrieved for username" username "with role" role ". Set X-Authorization-token cookie and assoc account in DB")
     {:cookie {"X-Authorization-Token" (:token keycloak-info)}
      :db (assoc db :my-account keycloak-info-without-unused-roles)
      :dispatch [:user-connected]})))

(reg-event-fx ::logout
  (fn [{:keys [db]} [_]]
    (debug "logout user")
    (re-frame/dispatch [:token-to-update/stop])
    (.logout (:keycloak-obj db))
    {:cookie nil}))

(reg-event-fx :token-to-update/tick
 (fn [cofx event]
   ;;call keycloak to update the token if it has more than 3 minutes of life
   (let [keycloak-obj (get-in cofx [:db :keycloak-obj])]
     (frontend/check-to-update-token
      keycloak-obj
      180
      (fn [refreshed-token]
        (when refreshed-token
          (dispatch [:electre.common.front.events/set-token-updated {:token refreshed-token}])))
      (fn []
        (.logout keycloak-obj))))
   {:db (:db cofx)}))

(reg-event-fx ::set-token-updated
 [(inject-cofx :cookie)]
 (fn [cofx [_ {:keys [token]}]]
   ;(debug "set token updated to" token)
   {:cookie (merge (:cookie cofx) {"X-Authorization-Token" token})}))

(defn dispatch-keycloak-info-retrieved [keycloak-obj user-info-clj]
  (dispatch [:electre.common.front.events/keycloak-info-retrieved
             (merge {:token (.-token keycloak-obj)}
                    {:username (:preferred_username user-info-clj)}
                    {:token-parsed (js->clj (.-tokenParsed keycloak-obj) :keywordize-keys true)}
                    user-info-clj )]))

(defn- start-token-refresher
  "Start the token refresher process with a check every interval-duration passed as an argument (in ms)"
  [interval-duration]
  ;; event triggered every 60s to check for the keycloak token to be updated
  (interval/register-interval-handlers :electre.common.front.events/token-to-update nil interval-duration)
  (re-frame/dispatch [:token-to-update/start]))

(re-frame/reg-fx :http-xhrio
                 (fn [request]
                   (-> request
                       (merge {:with-credentials true})
                       http-effect)))


(comment
  ;; The token is short lived so we must refresh it regularly, here we start the refresher "background" process thanks to core.async
  (start-token-refresher refresh-check-interval))


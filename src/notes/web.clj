(ns notes.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [hiccup.core :refer [h]]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.form :refer [form-to text-area text-field submit-button]]
            [cemerick.drawbridge :as drawbridge]
            [environ.core :refer [env]]))

(defn- authenticated? [user pass]
  ;; TODO: heroku config:add REPL_USER=[...] REPL_PASSWORD=[...]
  (= [user pass] [(env :repl-user false) (env :repl-password false)]))

(def ^:private drawbridge
  (-> (drawbridge/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(defn layout [title & body]
  (html5
   [:head
    [:title title]
    (include-css "/css/reset.css")]
   (into [:body] body)))

(defn info []
  (layout
   "info page"
   [:h1 "info page"]))

(defn message []
  (layout
   "Input Message"
   (form-to [:post "/"]
            (text-area {:placeholder "say something..."} "message")
            [:br]
            (text-field {:placeholder "name"} "id")
            (submit-button "post it!"))))

(defn show-message [id message]
  (layout
   "Show Message"
   [:p (h id) " says " (h message)]))

(defroutes app
  (ANY "/repl" {:as req}
       (drawbridge req))
  (GET "/" [] (message))
  (POST "/" [id message] (show-message id message))
  (GET "/info" [] (info))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

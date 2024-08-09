(ns xtdb-inspector.core
  "Core XTDB inspector ns, start new inspector server or
  get routes to integrate into an existing ring app."
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [xtdb-inspector.util :refer [->route]]
            [xtdb-inspector.page :as page]
            [xtdb-inspector.page.doc :as page.doc]
            [xtdb-inspector.page.query :as page.query]
            [xtdb-inspector.page.attr :as page.attr]
            [xtdb-inspector.page.tx :as page.tx]
            [xtdb-inspector.page.dashboard :as page.dashboard]
            [ripley.live.context :as context]
            [ring.middleware.params :as ring-params]))

(defn- page [{wrap :wrap-page-fn :as ctx} req page-fn]
  (let [handler
        (fn [req]
          (let [ctx (assoc ctx :request req)]
            (page/page-response
             ctx
             #(page-fn ctx))))]
    (if wrap
      (wrap req handler)
      (handler req))))

(defn inspector-handler
  ([xtdb-node] (inspector-handler xtdb-node {}))
  ([xtdb-node {:keys [allow-editing? wrap-page-fn]
               :or {allow-editing? true}}]
   (let [ctx {:xtdb-node xtdb-node
              :allow-editing? allow-editing?
              :wrap-page-fn wrap-page-fn}]
     (routes
      (context/connection-handler "/__ripley-live" :ping-interval 45)
      (GET (->route "/doc") req
        (page ctx req #'page.doc/render-form))
      (GET (->route "/doc/:doc-id") req
        (page ctx req #'page.doc/render))
      (GET (->route "/query") req
        (page ctx req #'page.query/render))
      (POST (->route "/query/export") req
        (page.query/export-query ctx req))
      (GET (->route "/query/:query") req
        (page ctx req #'page.query/render))
      (GET (->route "/attr") req
        (page ctx req #'page.attr/render))
      (GET (->route "/attr/:keyword") req
        (page ctx req #'page.attr/render))
      (GET (->route "/attr/:namespace/:keyword") req
        (page ctx req #'page.attr/render))
      (GET (->route "/tx") req
        (page ctx req #'page.tx/render))
      (GET (->route "/dashboard") req
        (page ctx req #'page.dashboard/render-listing))
      (GET (->route "/dashboard/:dashboard") req
        (page ctx req #'page.dashboard/render))
      (route/resources "/")))))

(defn start [{:keys [port xtdb-node allow-editing?]
              :or {port 3000
                   allow-editing? true}}]
  {:pre [(some? xtdb-node)]}
  (http-kit/run-server
   (ring-params/wrap-params
     (inspector-handler xtdb-node {:allow-editing? allow-editing?}))
   {:port port}))

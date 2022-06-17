(ns xtdb-inspector.ui.table
  "A table component with filtering."
  (:require [ripley.html :as h]
            [ripley.live.source :as source]
            [ripley.live.collection :as collection]
            [clojure.string :as str]
            [ripley.js :as js]))

(defn- default-filter-fn
  "A very simple text filter, just checks if printed representation
  includes the string (ignoring case)."
  [item text]
  (str/includes? (str/lower-case (pr-str item))
                 (str/lower-case text)))

(defn- filter-items [ordered-source? filter-fn items text [order-by order-direction]]
  (let [items (into []
                    (filter #(filter-fn % text))
                    items)]
    (if (and order-by (not ordered-source?))
      ((case order-direction
         :asc identity
         :desc reverse)
       (sort-by order-by items))
      items)))


(defn- render-row [{:keys [columns]} row]
  (h/html
   [:tr
    [::h/for [{:keys [accessor render]} columns
              :let [data (accessor row)
                    data (if render data (str data))]]
     [::h/if render
      [:td (render data)]
      [:td data]]]]))

(defn- filter-input [set-filter!]
  (let [id (str (gensym "table-filter"))]
    (h/html
     [:div {:class "my-2 flex sm:flex-row flex-col"}
      [:div.block.relative
       [:span.h-full.absolute.inset-y-0.left-0.flex.items-center.pl-2
        [:svg.h-4.w-4.fill-current.text-gray-500 {:viewBox "0 0 24 24"}
         [:path {:d "M10 4a6 6 0 100 12 6 6 0 000-12zm-8 6a8 8 0 1114.32 4.906l5.387 5.387a1 1 0 01-1.414 1.414l-5.387-5.387A8 8 0 012 10z"}]]]
       [:input {:id id
                :class "appearance-none rounded-r rounded-l sm:rounded-l-none border border-gray-400 border-b block pl-8 pr-6 py-2 w-full bg-white text-sm placeholder-gray-400 text-gray-700 focus:bg-white focus:placeholder-gray-600 focus:text-gray-700 focus:outline-none"
                :placeholder "Filter..."
                :on-input (js/js-debounced 300 set-filter!
                                            (js/input-value id))}]]])))

(defn- header [{:keys [columns]} set-order! [order-by order-direction]]
  (h/html
   [:thead
    [:tr
     [::h/for [{:keys [label accessor order-by?]
                :or {order-by? true}} columns]
      [:th {:on-click #(when order-by?
                         (set-order! [accessor (case order-direction
                                                 :asc :desc
                                                 :desc :asc)]))}
       label
       (when (and (= order-by accessor))
         (h/out! (case order-direction
                   :asc " \u2303"
                   :desc " \u2304")))]]]]))

(defn table
  "A data table that allows ordering by columns and filtering.

  Takes two arguments: an options map and the live source for items.

  Options:

  :columns    collection of columns for the table. Each column is a map
              containing at least :label and :accessor.
              Column may contain :render which is called to render the value.
              Default render just stringifies the value.
              If :order-by? is false, then this column can't be ordered by.

              Example: [{:label \"Name\" :accessor :name}
                        {:label \"Email\" :accessor :email}]

  :filter-fn  predicate that is called with item and current filter text
              default implementation just checks the printed representation
              of the item for a substring match.

  :order      the initial order [accessor direction] (eg. [:name :asc])

  :set-order! if specified, ordering will be done at the source level
              and not by the table. If the source is an XTDB query,
              it should handle the ordering in the query.
              If not set, the items are ordered by using clojure builtin
              `sort-by` function.
  "
  [{:keys [key filter-fn order set-order!]
              :or {filter-fn default-filter-fn
                   key identity
                   order [nil :asc]} :as table-def} data-source]
  (let [[filter-source set-filter!] (source/use-state "")
        [order-source set-table-order!] (source/use-state order)
        rows-source (source/computed
                     (partial filter-items (some? set-order!) filter-fn)
                     data-source filter-source order-source)]
    (h/html
     [:div.mx-2
      (filter-input set-filter!)
      [:table.table-auto
       [::h/live order-source (partial header table-def
                                       #(do
                                          (when set-order!
                                            (set-order! %))
                                          (set-table-order! %))) ]
       (collection/live-collection
        {:render (partial render-row table-def)
         :key key
         :container-element :tbody
         :source rows-source})]])))

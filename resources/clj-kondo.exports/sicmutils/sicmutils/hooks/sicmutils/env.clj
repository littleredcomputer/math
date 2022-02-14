(ns hooks.sicmutils.env
  (:require [clj-kondo.hooks-api :as api]))

(defn- ->declare [n]
  (api/list-node
   [(api/token-node 'declare) n]))

(defn bootstrap-repl!
  "Generates a form that looks like

  ```clj
  (do (declare 'x) (declare 'y) ,,,)
  ```

  For all vars exported by the `sicmutils.env` namespace."
  [{:keys [_node]}]
  (let [analysis (api/ns-analysis 'sicmutils.env)
        entries  (into (:clj analysis) (:cljs analysis))
        xform    (comp (filter
                        (comp #{'sicmutils.env} :ns val))
                       (map (comp ->declare api/token-node key)))
        declares (into [] xform entries)
        new-node (api/list-node
                  (list*
                   (api/token-node 'do)
                   declares))]
    {:node new-node}))

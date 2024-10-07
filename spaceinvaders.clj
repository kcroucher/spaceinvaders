(ns spaceinvaders
  (:import [java.awt.event KeyEvent KeyListener]
           [java.io File]
           java.lang.System
           [javax.swing JFrame JPanel]
           [javax.imageio ImageIO]))

;; Init frame
(def frame-width 600)
(def frame-height 500)

;; Run game at 60 frames per second
(def millis-per-frame (/ 1000 60))

;; Init player
(def player-image (ImageIO/read (File. "player.png")))
(def player-width (.getWidth player-image))
(def player-height (.getHeight player-image))
(def player-max-pos (- frame-width player-width))

(def player-speed 2)

(def player
  (atom
   {:x (float
        (-
         (/ frame-width 2)
         (/ player-width 2)))
    :y (float
        (- frame-height 100))}))

;; Init enemies
(def enemy-image-1 (ImageIO/read (File. "enemy1.png")))
(def enemy-image-2 (ImageIO/read (File. "enemy2.png")))
(def enemy-width (.getWidth enemy-image-1))
(def enemy-height (.getHeight enemy-image-1))
(def enemy-max-pos-right (- frame-width enemy-width))

(def enemy-row-size 7)
(def enemy-col-size 5)
(def enemy-grid-spacing-x 14)
(def enemy-grid-spacing-y 10)
(def enemy-grid-start-x 10)
(def enemy-grid-start-y 10)

(def enemy-speed-x 1)
(def enemy-speed-y 4)

(def enemies
  (atom
   (for [x (range enemy-row-size)
         y (range enemy-col-size)]
     {:x (float
          (+
           enemy-grid-start-x
           (* x enemy-width)
           (* x enemy-grid-spacing-x)))
      :y (float
          (+
           enemy-grid-start-y
           (* y enemy-height)
           (* y enemy-grid-spacing-y)))})))

(def enemy-direction (atom :right))
(defn is-horizontal-direction [direction]
  (contains? #{:left :right} direction))
(defn get-next-enemy-direction [direction]
  (case direction
    :right :down-then-left
    :down-then-left :left
    :left :down-then-right
    :down-then-right :right))
(defn get-enemy-image-for-frame []
  (if (< (rem (System/currentTimeMillis) 2000) 1000)
    enemy-image-1
    enemy-image-2))

;; Input
(def active-input (atom #{}))
(def key-listener (proxy [KeyListener] [] 
                    (keyPressed [key-event]
                      (swap! active-input #(conj % (.getKeyCode key-event))))
                    (keyReleased [key-event]
                      (swap! active-input #(disj % (.getKeyCode key-event))))
                    (keyTyped [key-event])))

;; Describe how to draw objects
(def panel (proxy [JPanel] []
             (paintComponent [graphics]
               (proxy-super paintComponent graphics)
              ;;  Draw player
               (.drawImage
                graphics
                player-image
                (int (:x @player))
                (int (:y @player))
                this)
              ;;  Draw enemies
               (doseq [enemy @enemies]
                 (.drawImage
                  graphics
                  (get-enemy-image-for-frame)
                  (int (:x enemy))
                  (int (:y enemy))
                  this))
              ;;  Draw score
               (.drawString
                graphics
                (str "Score: " 0)
                20
                (- frame-height 50)))))

;; Set up main window
(def frame (JFrame. "Space Invaders"))
(doto frame
  (.setSize frame-width frame-height)
  (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
  (.addKeyListener key-listener)
  (.add panel)
  (.show)
  (.setAlwaysOnTop true))

;; Game logic helpers
(defn get-new-player-pos [displacement current-player]
  (update current-player :x
          (fn [current-pos]
            (->> current-pos
                 (+ displacement)
                 (min player-max-pos)
                 (max 0)))))

(defn get-enemy-movement-distance-for-frame [enemies direction]
  (if (is-horizontal-direction direction)
    (reduce (fn [min-distance enemy]
              (let [distance-from-edge (case direction
                                         :right (- enemy-max-pos-right (:x enemy))
                                         :left (:x enemy))]
                (min min-distance distance-from-edge)))
            enemy-speed-x
            enemies)
    enemy-speed-y))
(defn get-new-enemies-pos [enemies component update-pos-fn]
  (map #(update % component update-pos-fn) enemies))

;; Game logic
(defn process-game-logic-for-frame []
  ;; Move player
  (when (get @active-input KeyEvent/VK_RIGHT)
    (swap! player (partial get-new-player-pos player-speed)))
  (when (get @active-input KeyEvent/VK_LEFT)
    (swap! player (partial get-new-player-pos (- player-speed))))
  (when (get @active-input KeyEvent/VK_SPACE)
    (println "Spacebar pressed"))
  ;; Move enemies
  (let [direction @enemy-direction
        movement-distance (get-enemy-movement-distance-for-frame @enemies direction)]
    (if (is-horizontal-direction direction)
      (do
        ;; Move enemies horizontally
        (swap! enemies get-new-enemies-pos :x (fn [old-pos] 
                                               (case direction 
                                                 :right (+ old-pos movement-distance) 
                                                 :left (- old-pos movement-distance))))
        ;; Change direction if we hit the frame edge
        (when (< movement-distance enemy-speed-x)
          (swap! enemy-direction get-next-enemy-direction)))
      
      (do
        ;; Move enemies vertically
        (swap! enemies get-new-enemies-pos :y (fn [old-pos] 
                                               (+ old-pos movement-distance)))
        ;; Always change direction, we always move vertically exactly one frame
        (swap! enemy-direction get-next-enemy-direction)))))

(defn draw-frame []
  (.repaint panel))

;; Main game loop
(loop [last-frame-time (System/currentTimeMillis)
       this-frame-time last-frame-time
       leftover-time 0]
  (let [elapsed-time (- this-frame-time last-frame-time)
        total-time (+ elapsed-time leftover-time)
        frames-to-process (quot total-time millis-per-frame)]
    (dotimes [_ frames-to-process]
      (process-game-logic-for-frame))
    (draw-frame)
    (recur
     this-frame-time
     (System/currentTimeMillis)
     (rem total-time millis-per-frame))))
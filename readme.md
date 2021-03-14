Java Annotate
=============

This is a simple Java-based program that allows annotating a region of your screen, such as might be used for screen-captures or other kinds of presentation.

It can provide a plain whiteboard for drawing, or a transparent window (on OS installations that support this) so that existing windows can be annotated/highlighted, using either solid or transluscent "pens".

(I would have used Ardesia, but it doesn't work on my system...)

Note
----

On Linux systems I've noticed two problems:

1) If you have multiple screens, it seems that only the "primary"
   screen supports transparency, 
   this is particularly odd, since it's possible to position the 
   drawing canvas so that it's partly on other screens, and the
   transparency still works. But if the window manager decides the 
   canvas is "hosted" (I'm not entirely sure what the decision is, frankly)
   on one of the other screens, then transparency fails 
   
1) On Java 11 and 15, the transluscent drawing (i.e. the highlighter 
   effect) fails and things show up as a kind of XOR type plotting.
   The effect works just fine with Java 8. I don't yet know if this
   is an error in my coding that doesn't show up in Java 8, or if it's
   some change or problem with newer versions of Java. Right now
   I'm simply keeping a Java 8 JDK and running with that.




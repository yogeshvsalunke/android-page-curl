# Page Curl for Android #
## <font color='red'>The project has been moved to GitHub</font> ##
We have decided to move the whole project over to GitHub so that it will be easier to for your own and to maintain contributions.

Feel free to clone it from here: https://github.com/MysticTreeGames/android-page-curl
## Overview ##
The android-page-curl is a 2D View which simulates a page curl effect. Without OpenGL, only the android canvas has been used, so that it can be used in any version of Android!

## Showcase ##
### Page Turner ###
The synchronizing e-reader for Android.

_PageTurner is an open-source e-book reader for Android that allows you to synchronize your reading progress across devices. This means you can read a few pages on your phone and then grab your tablet and pick up on the exact spot you left off on your phone. Grab quick reading moments anywhere, anytime._

PageTurner does not use the android-page-curl out of the box, they transformed it into an animator class! It's a great reader and of course all open-source!

For more info: http://www.pageturner-reader.org/

## Demo ##
<a href='http://www.youtube.com/watch?feature=player_embedded&v=aVZHN_o45sg' target='_blank'><img src='http://img.youtube.com/vi/aVZHN_o45sg/0.jpg' width='425' height=344 /></a>

## Current State ##
By now it just switches between 2 images. But the idea is to render any view to animage and using two (foregound and background) images to create the effect. In some thime the flipper will inherit from ViewGroup instead if View so that content providers and adapters will be able to add views to it.
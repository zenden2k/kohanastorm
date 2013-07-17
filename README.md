kohanastorm
===========

Kohana 3 helper plugin for PHPStorm

It provides two simple features:

- Navigate to a template from controller (from View::factory('...') )
- Navigate to a controller by URL

Quickly navigate to your controller's action by URL.
In order to enable this feature, you need to add the following code in the end of your boostrap.php:

if ( !empty( $_GET['ks_secret_key'] ) &&  ($_SERVER['REMOTE_ADDR'] == '127.0.0.1' || $_GET['ks_secret_key'] == 'your Secret Key' ) ){
    $req = Request::factory();
    die( 'KS;1;'.$req->directory().';'.$req->controller() .';'.$req->action() );
}

For older versions (Kohana 3.0):

if ( ( !empty( $_GET['ks_enable'] ) &&   $_SERVER['REMOTE_ADDR'] == '127.0.0.1' )
    ||  !empty( $_GET['ks_secret_key']) && $_GET['ks_secret_key'] == 'your secret key' ) {
    $req = Request::instance();
    die( 'KS;1;'.$req->directory.';'.$req->controller .';'.$req->action );
}

Now you can press Ctrl+Shift+P in your project, paste url into the text box and your controller class will be opened in code editor.
Enjoy!

You can get compiled jar here: https://image-uploader.googlecode.com/files/KohanaStorm.jar

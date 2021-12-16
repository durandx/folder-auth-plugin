'use strict';

// noinspection JSUnusedGlobalSymbols
/**
 * Adds a Folder Role
 */
const addFolderRoleDelegate = (itemUrl, folderName) => {
    const roleName = document.getElementById('folderRoleName').value;
    if (!roleName || roleName.length < 3) {
        alert('Please enter a valid name for the role to be added');
        return;
    }

    const response = {
        name: roleName,
        permissions: document.getElementById('folder-permission-select').getValue(),
        folderNames: [folderName],
    };

    if (!response.permissions || response.permissions.length <= 0) {
        alert('Please select at least one permission');
        return;
    }

    //sendPostRequest(`${rootURL}/delegate/addFolderRole`, response);
    sendPostRequest(`${itemUrl}/addFolderRole`, response);
};

/**
 * Sends a POST request to {@code postUrl}
 * @param postUrl the URL
 * @param json JSON data to be sent
 */
const sendPostRequest = (postUrl, json) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', postUrl, true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    // Jelly file sets up the crumb value for CSRF protection
    if (crumb.value) {
        xhr.setRequestHeader('Jenkins-Crumb', crumb.value);
    }

    xhr.onload = () => {
        if (xhr.status === 200) {
            alert('The role was added successfully');
            location.reload(); // refresh the page
        } else {
            alert('Unable to add the role\n' + xhr.responseText);
        }
    };

    // this is really bad.
    // See https://github.com/jenkinsci/jenkins/blob/75468da366c1d257a51655dcbe952d55b8aeeb9c/war/src/main/js/util/jenkins.js#L22
    const oldPrototype = Array.prototype.toJSON;
    delete Array.prototype.toJSON;

    try {
        xhr.send(JSON.stringify(json));
    } finally {
        Array.prototype.toJSON = oldPrototype;
    }
};

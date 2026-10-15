"""Run the existing editing/export/thumbnail contracts against new v2 projects."""
from unittest import mock

import test_studio
import test_studio_scripts
import test_studio_thumbnails
from studio_core.project import Project


def v2_contract(name, original, excluded=(), version=2):
    def setUp(self):
        create = Project.create
        def new_project(*args, **kwargs):
            kwargs['version'] = version
            return create(*args, **kwargs)
        with mock.patch.object(Project, 'create', side_effect=new_project):
            original.setUp(self)
    # Journal injection and externally edited revision semantics differ by
    # format and have dedicated v2 coverage in test_studio_files.py.
    return type(name, (original,), {'setUp':setUp, **{method:None for method in excluded}})


V2Projects = v2_contract('V2Projects', test_studio.Projects)
V2Thumbnails = v2_contract('V2Thumbnails', test_studio_thumbnails.Thumbnails)
V2Scripts = v2_contract('V2Scripts', test_studio_scripts.ScriptWorkspace, (
    'test_recovery_finishes_an_interrupted_multi_file_commit',
    'test_recovery_preserves_new_external_changes',
    'test_extract_preserves_native_map_and_external_edits_are_authoritative',
    'test_preview_rechecks_expected_versions_after_loading_under_lock',
))

V3Projects = v2_contract('V3Projects', test_studio.Projects, version=3)
V3Thumbnails = v2_contract('V3Thumbnails', test_studio_thumbnails.Thumbnails, version=3)
V3Scripts = v2_contract('V3Scripts', test_studio_scripts.ScriptWorkspace, (
    'test_recovery_finishes_an_interrupted_multi_file_commit',
    'test_recovery_preserves_new_external_changes',
    'test_extract_preserves_native_map_and_external_edits_are_authoritative',
    'test_preview_rechecks_expected_versions_after_loading_under_lock',
),version=3)
